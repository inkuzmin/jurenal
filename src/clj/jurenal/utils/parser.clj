(ns jurenal.utils.parser
  (:require
    [clojure.walk :as w :refer [walk]]
    [hickory.core :as hc]
    [hickory.render :as hr]
    [markdown.core :as md]
    [markdown.common :as mdcom]
    [markdown.transformers :as mdtrans]
    [markdown.lists :as mdlist]
    [markdown.links :as mdlinks]
    [jurenal.utils.emoji :as emoji]
    [clojure.string :as string]
    [jurenal.utils.emoji :as emoji]
    [clojure.tools.logging :as log]))


(defn filter-link [link]
  (let [url (re-find #"https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)"
                     link)]
    (if url (first url) "")))

(defn replace-links [text]
  (string/replace
    text
    #"\[(.*?)\]\(<url>(.*?)</url>\)"
    #(let [title (nth % 1)
           url (nth % 2)]
      (str "<url title=\"" title "\">" url "</url>"))))

(defn replace-images [text]
  (string/replace
    text
    #"!\[(.*?)\]\(<url>(.*?)</url>\)"
    #(let [alt (nth % 1)
           url (nth % 2)]
      (str "<url alt=\"" alt "\">" url "</url>"))))

(defn replace-emphs [text]
  (string/replace
    text
    #"(\*|_)(.*?)\1"
    #(let [t (nth % 2)]
      (str "<em>" t "</em>"))))

(defn replace-linebreaks [text]
  (string/replace
     text
     #"\n"
     (str "<br>")))

(defn replace-quotes [text]
  (string/replace
    text
    #"(?m)^>(.*?)(?:$|\n)"
    #(let [t (second %)]
      (str "<blockquote>" t "</blockquote>"))))

(defn replace-lists [text]
  (string/replace
    text
    #"(?m)^\*(.*?)($|\n)"
    #(let [t (second %)
           eol (nth % 3 nil)]
      (str "<li>" t "</li>"))))


(defmacro fixgen [entities]
  (let [t (gensym)
        e (gensym)
        s (gensym)
        start (gensym)
        end (gensym)]
    `(fn [~t ~e ~s]
       (let [~start (+ (:offset ~e) ~s)
             ~end (+ (:offset ~e) (:length  ~e) ~s)]
         (cond
           ~@(mapcat identity
               (for [entity entities]
                  [`(= ~entity (:type ~e))
                   `(let
                     [before# (subs ~t 0 ~start)
                      replaced# (str "<" ~entity ">"
                                     (subs ~t ~start ~end)
                                     "</" ~entity ">")
                      after# (subs ~t ~end)]
                     [(str before# replaced# after#) ~(+ 5 (* 2 (count entity)))])]))
           :else
           [~t 0])))))



(def fix (fixgen ["mention" "bot_command" "hashtag"
                  "url" "email" "bold" "italic" "code"
                  "pre" "text_link" "text_mention"]))



(defn fix-text [text enitities]
  (loop [es enitities
         t text
         s 0]
    (if (seq es)
      (let [[fixed-t fixed-s] (fix t (first es) s)]
        (recur (rest es) fixed-t (+ s fixed-s)))
      t)))


(defn find-triggers [t e cs]
  (let [start (:offset e)
        end (+ (:offset e) (:length e))]
    (cond
      (= "mention" (:type e))
      (let
        [mention (subs t start end)]
        (assoc cs :mentions (conj (:mentions cs) mention)))

      (= "bot_command" (:type e))
      (let
        [cmd (subs t start end)]
        (assoc cs :commands (conj (:commands cs) cmd)))

      (= "hashtag" (:type e))
      (let
        [tag (subs t start end)]
        (assoc cs :hashtags (conj (:hashtags cs) tag)))

      :else
      cs)))


(defn parse-message [{:keys [text entities]}]
  (let [fixed-text (fix-text text entities)
        re (re-find #"(?s)(?:^|\s):(\d+)(?:(\s(.*))|$)" fixed-text)
        reply (when (get re 1) (read-string (get re 1)))
        refixed-text (or (get re 3) fixed-text)
        reaction (when reply (emoji/emoji? refixed-text))
        cmds
          (loop [es entities
                 t text
                 cs {:mentions []
                     :commands []
                     :hashtags []}]
            (if (seq es)
              (let [new-cs (find-triggers t (first es) cs)]
                (recur (rest es) t new-cs))
              cs))]
    {:text refixed-text
     :source text
     :reply reply
     :react reaction
     :mentions (:mentions cmds)
     :commands (:commands cmds)
     :hashtags (:hashtags cmds)}))


(defn autourl-transformer [text state]
  [(if (:code state)
     text
     (clojure.string/replace
       text
       #"https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)"
       #(let [url (first %)]
         (str "<a href=\"" url "\">" url "</a>"))))
   state])



(defn image? [link]
  (or (string/ends-with? (string/lower-case link) ".jpg")
      (string/ends-with? (string/lower-case link) ".jpeg")
      (string/ends-with? (string/lower-case link) ".png")))



(defn transform [node]
  (let [content (first (:content node))]
    (cond
      (= :bot_command (:tag node))
      nil

      (= :code (:tag node))
      node

      (= :li (:tag node))
      node

      (= :blockquote (:tag node))
      node

      (= :br (:tag node))
      node

      (= :pre (:tag node))
      node

      (= :url (:tag node))
      (if (image? content)
        (do
          (-> node
              (assoc :tag :img)
              (assoc :attrs {:src (filter-link content)
                             :alt (or (get-in node [:attrs :alt]) "pic without description")})))
        (do
          (-> node
              (assoc :tag :a)
              (assoc :content [(or (get-in node [:attrs :title]) content)])
              (assoc :attrs {:href (filter-link content)}))))


      (= :mention (:tag node))
      (-> node
           (assoc :tag :a)
           (assoc :attrs {:href (str "/#/" content)})))))



(defn strip [html]
  (reduce
    str
    (map hr/hickory-to-html
         (walk
           (fn [node]
             (if (= :element (:type node))
               (transform node)
               {:type :element
                :tag :span
                :content (map hc/as-hickory (hc/parse-fragment node))}))
           #(filter (comp not nil?) %)
           (map hc/as-hickory (hc/parse-fragment html))))))



(defn parse [source]
  (-> source
      strip
      replace-images
      replace-links
      replace-linebreaks
      emoji/replace-emoji
      emoji/replace-aliases))

