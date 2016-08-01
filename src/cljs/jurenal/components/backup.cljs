(ns jurenal.components.backup)

;(defn nav-link [uri title page collapsed?]
;  [:li.nav-item
;   {:class (when (= page (session/get :page)) "active")}
;   [:a.nav-link
;    {:href uri
;     :on-click #(reset! collapsed? true)} title]])
;
;(defn navbar []
;  (let [collapsed? (r/atom true)]
;    (fn []
;      [:nav.navbar.navbar-light.bg-faded
;       [:button.navbar-toggler.hidden-sm-up
;        {:on-click #(swap! collapsed? not)} "☰"]
;       [:div.collapse.navbar-toggleable-xs
;        (when-not @collapsed? {:class "in"})
;        [:a.navbar-brand {:href "#/"} "jurenal"]
;        [:ul.nav.navbar-nav
;         [nav-link "#/" "Home" :home collapsed?]
;         [nav-link "#/about" "About" :about collapsed?]]]])))
;
;(defn about-page []
;  [:div.container
;   [:div.row
;    [:div.col-md-12
;     "this is the story of jurenal... work in progress"]]])
;
;
;(defn home-page-backup []
;  [:div.container
;   [:div.jumbotron
;    [:h1 "Welcome to jurenal"]
;    [:p "Time to start building your site!"]
;    [:p [:a.btn.btn-primary.btn-lg {:href "http://luminusweb.net"} "Learn more »"]]]
;   [:div.row
;    [:div.col-md-12
;     [:h2 "Welcome to ClojureScript"]]]
;   (when-let [docs (session/get :docs)]
;     [:div.row
;      [:div.col-md-12
;       [:div {:dangerouslySetInnerHTML
;              {:__html (md->html docs)}}]]])])