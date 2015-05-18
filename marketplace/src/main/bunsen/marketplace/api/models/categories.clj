(ns bunsen.marketplace.api.models.categories
  (:require [bunsen.marketplace.helper.api :as helper]
            [bunsen.marketplace.base :as base]
            [clojurewerkz.elastisch.rest.response :as res]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.index :as ind]
            [clojurewerkz.elastisch.rest.document :as doc]))

(declare build-query)

(defn add-id-for-elastisch
  "Adds the _id element (copied from existing 'id') to each category"
  [categories]
  (map (fn [cat] (assoc cat :_id (:id cat)))
       categories))

(defn get-with-params
  "Returns categories based on supplied parameters.

  Typeahead -
  index-name = index which category belongs to
  search-term = three or more characters which will search against category name

  Category Tree -
  root = root of category's path
  limit = limit length of path"
  [config params]
  (let [query (build-query params)
        search (doc/search (helper/connect-to-es config)
                           (or (:index-name params) "*")
                           "categories"
                           :size (or (:size params) 25)
                           :query query)]
    (map #(merge (:_source %) {:index (:_index %)})
         (-> search :hits :hits))))

(defn create-bulk
  "Returns true if categories payload was succesfully sent to
  ElasticSearch, false otherwise."
  [es-conn index-name payload]
  (let [categories (:categories payload)
        indexer (base/index! es-conn index-name "categories" categories
                             identity ; json already parsed
                             add-id-for-elastisch
                             base/bulk-to-es!)]
    (await-for 5000 indexer)
    (= (:stage @indexer) :indexed)))

(defn build-query [params]
  (cond
    ; Typeahead
    (and (:index-name params) (:search-term params))
    {:fuzzy_like_this_field {"name" {:like_text (:search-term params)}}}
    ; Category tree.
    (or (:root params) (:limit params))
    {:regexp {:path (format "%s(\\.[0-9]*){0,%s}" (or (:root params) 0) (or (:limit params) 0))}}
    ; Catch all
    :else
    {}))

(defn fetch
  "Fetches a single category within a given catalog and having a matching index"
  [es-conn index catalog-path]
  (-> (doc/search es-conn
                  index
                  "categories"
                  :size 1
                  :query {:term {:path catalog-path}})
      :hits :hits first :_source))

(defn fetch-count
  "Issues an ES query for the count for the datasets belonging
  a given category path"
  [es-conn index-name path]
  (doc/count
    es-conn index-name "datasets"
    (q/bool {:should [(q/prefix :path (str path "."))
                      (q/term :path path)]})))

(defn parse-count
  "Given an ES response, return [:result count-from-response]"
  [response]
  (res/count-from response))

(defn update-es-count!
  "Given a specific entity id in elastic search, update its count attribute
  in place"
  [id es-conn index-name mapping-type count]
  (doc/update-with-partial-doc es-conn index-name mapping-type id
                               {:count count}))

(defn update-counts!
  "Given ES connection and category map, updates count attributes of
  all categories therein"
  [es-conn index-name categories]
  (doseq [category categories]
    (let [[id {:keys [path] :as attrs}] category
          count (parse-count (fetch-count es-conn index-name path))]
      (update-es-count! id es-conn index-name "categories" count))))

(defn background-update-counts
  "Updates datasets within an index with the correct count, this method
  is intended to be run after a CRUD operation"
  [es-conn index-name]
  (let [categories (base/read-indexed-results es-conn index-name "categories")]
    (future
      (ind/refresh es-conn index-name)
      (update-counts! es-conn index-name categories))))
