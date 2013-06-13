(ns clojars.web.jar
  (:require [clojars.web.common :refer [html-doc jar-link group-link
                                        tag jar-url jar-name user-link
                                        jar-fork? single-fork-notice
                                        simple-date]]
            [hiccup.element :refer [link-to]]
            [hiccup.form :refer [submit-button]]
            [clojars.web.safe-hiccup :refer [form-to]]
            [clojars.maven :refer [jar-to-pom-map commit-url]]
            [clojars.auth :refer [authorized?]]
            [clojars.db :refer [find-jar]]
            [clojars.promote :refer [blockers]]
            [clojars.stats :as stats]
            [clojure.set :as set]
            [ring.util.codec :refer [url-encode]]))

(defn url-for [jar]
  (str (jar-url jar) "/versions/" (:version jar)))

(defn maven-jar-url [jar]
 (str "http://search.maven.org/#"
   (url-encode (apply format "artifactdetails|%s|%s|%s|jar"
        ((juxt :group_name :jar_name :version) jar)))))

(defn dependency-link [dep]
  (let [url (if (find-jar (:group_name dep) (:jar_name dep))
              (jar-url dep)
              (maven-jar-url dep))]
    (link-to url (str (jar-name dep) " " (:version dep)))))

(defn dependency-section [title id dependencies]
  (if (empty? dependencies) '()
    (list
    [:h3 title]
    [(keyword (str "ul#" id))
     (for [dep dependencies]
       [:li (dependency-link dep)])])))

; handles link-to throwing an exception when given a non-url
(defn safe-link-to [url text]
  (try (link-to url text)
    (catch Exception e text)))

(defn fork-notice [jar]
  (when (jar-fork? jar)
    single-fork-notice))

(defn promotion-details [account jar]
  (if (authorized? account (:group_name jar))
    (list [:h3 "promotion"]
          (if (:promoted_at jar)
            [:p (str "Promoted at " (java.util.Date. (:promoted_at jar)))]
            (if-let [issues (seq (blockers (set/rename-keys
                                            jar {:group_name :group
                                                 :jar_name :name})))]
              [:ul#blockers
               (for [i issues]
                 [:li i])]
              (form-to [:post (str "/" (:group_name jar) "/" (:jar_name jar)
                                   "/promote/" (:version jar))]
                       (submit-button "Promote")))))))

(defn show-jar [account jar recent-versions count]
  (html-doc account (str (:jar_name jar) " " (:version jar))
            [:div.grid_9.alpha
             [:h1 (jar-link jar)]]
            [:div.grid_3.omega
             [:small.downloads
              (let [stats (stats/all)]
                [:p
                 "Downloads: "
                 (stats/download-count stats
                                       (:group_name jar)
                                       (:jar_name jar))
                 [:br]
                 "This version: "
                 (stats/download-count stats
                                       (:group_name jar)
                                       (:jar_name jar)
                                       (:version jar))])]]
            [:div.grid_12.alpha.omega
             (:description jar)
             (when-let [homepage (:homepage jar)]
               [:p.homepage (safe-link-to homepage homepage)])
             [:div {:class "useit"}
              [:div {:class "lein"}
               [:h3 "leiningen"]
               [:pre
                (tag "[")
                (jar-name jar)
                [:span {:class :string} " \""
                 (:version jar) "\""] (tag "]") ]]

              [:div {:class "maven"}
               [:h3 "maven"]
               [:pre
                (tag "<dependency>\n")
                (tag "  <groupId>") (:group_name jar) (tag "</groupId>\n")
                (tag "  <artifactId>") (:jar_name jar) (tag "</artifactId>\n")
                (tag "  <version>") (:version jar) (tag "</version>\n")
                (tag "</dependency>")]]
              (let [pom (jar-to-pom-map jar)]
                (list
                 [:p "Pushed by " (user-link (:user jar)) " on "
                  [:span {:title (str (java.util.Date. (:created jar)))} (simple-date (:created jar))]
                  (if-let [url (commit-url pom)]
                    [:span.commit-url " with " (link-to url "this commit")])]
                 (fork-notice jar)
                 (promotion-details account jar)
                 (dependency-section "dependencies" "dependencies"
                                     (remove #(not= (:scope %) "compile") (:dependencies pom)))
                 (when-not pom
                   [:p.error "Oops. We hit an error opening the metadata POM file for this jar "
                    "so some details are not available."])))
              [:h3 "recent versions"]
              [:ul#versions
               (for [v recent-versions]
                 [:li (link-to (url-for (assoc jar
                                          :version (:version v)))
                               (:version v))])]
              [:p (link-to (str (jar-url jar) "/versions")
                           (str "show all versions (" count " total)"))]]]))

(defn show-versions [account jar versions]
  (html-doc account (str "all versions of "(jar-name jar))
            [:h1 "all versions of "(jar-link jar)]
            [:div {:class "versions"}
             [:ul
              (for [v versions]
                [:li (link-to (url-for (assoc jar
                                         :version (:version v)))
                              (:version v))])]]))
