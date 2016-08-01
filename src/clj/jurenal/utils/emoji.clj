(ns jurenal.utils.emoji
  (:require [clojure.java.io :as io]
            [cheshire.core :refer :all]))

(def emoji-map
  (into {}
    (map (fn [e]
            [(keyword (:short_name e))
             (if (:skin_variations e)
               (assoc e :skin_variations (map second (sort-by first (:skin_variations e))))
               e)])
        (parse-stream (io/reader (io/resource "emoji.json")) true))))

(defn to-unicode [alter]
  (com.vdurmont.emoji.EmojiParser/parseToUnicode alter))

(defn purify [alter]
  (subs alter 1 (dec (count alter))))

(defn get-emoji [alter]
  (let [pure (purify alter)
        data (clojure.string/split pure #"\|")
        k (keyword (first data))
        type (if-let [t (second data)]
               (- (read-string (str (last t))) 2))
        emoji (get emoji-map k)
        variant (when type (nth (:skin_variations emoji) type nil))]
    {:name (:name emoji)
     :short_name (:short_name emoji)
     :unicode (to-unicode alter)
     :unified (if variant (:unified variant) (:unified emoji))
     :sheet_x (if variant (:sheet_x variant) (:sheet_x emoji))
     :sheet_y (if variant (:sheet_y variant) (:sheet_y emoji))}))

(defn emoji-to-image [emoji]
  (str "<img class=\"emoji\" "
       "src=\"/img/emoji/" (clojure.string/lower-case (:unified emoji)) ".png\" "
       "alt=\"" (:unicode emoji) "\" />"))


(defn alter-to-image [alter]
  (emoji-to-image (get-emoji alter)))


(defn replace-aliases [text]
  (clojure.string/replace
    text
    #":[\w\|\_]+:"
    #(alter-to-image %)))

(defn replace-emoji [text]
  (com.vdurmont.emoji.EmojiParser/parseToAliases text))


(defn emoji? [text]
  (and
       (re-matches #"^:[\w\|\_\+]+:$" (replace-emoji text))
       (let [pure (purify (replace-emoji text))
             data (clojure.string/split pure #"\|")
             k (keyword (first data))]
         (contains? emoji-map k))))


(defn get-emoji-by-code [unicode]
  (replace-emoji unicode))