(ns factui.impl.session-test
  (:require
    #?(:cljs [factui.api :as api :include-macros true]
       :clj [factui.api :as api])
   #?(:clj [clara.rules :as cr]
       :cljs [clara.rules :as cr :include-macros true])
    [clojure.pprint :refer [pprint]]
   #?(:clj [clojure.test :as t :refer [deftest is testing run-tests]]
      :cljs [cljs.test :as t :refer-macros [deftest is testing run-tests]])
   #?(:cljs [factui.facts :as f :refer [Datom]]
      :clj [factui.facts :as f]))
  #?(:clj (:import [factui.facts Datom])))

(def test-schema
  [{:db/ident :person/id
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :person/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :person/likes
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/many}
   {:db/ident :person/age
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident :person/friends
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}])

(api/defquery-static person-by-pid
  [:find ?a ?v
   :in ?pid
   :where
   [?e :person/id ?pid]
   [?e ?a ?v]])

(cr/defrule popeye-likes-spinach
  [:person/name [{:keys [e v]}] (= e ?e) (= v "Popeye")]
  =>
  (api/transact! [{:db/id ?e
                   :person/likes "spinach"}]))

(cr/defrule locutus-likes-assimilation
  ;; But only when he is Locutus
  [:person/name [{:keys [e v]}] (= e ?e) (= v "Locutus")]
  =>
  (api/transact-logical! [{:db/id ?e
                           :person/likes "assimilation"}]))

(cr/defrule picard-likes-tea
  ;; But only when he is Picard
  [:person/name [{:keys [e v]}] (= e ?e) (= v "Picard")]
  =>
  (api/transact-logical! [{:db/id ?e
                           :person/likes "tea"}]))

(api/defquery-static all-attrs
  [:find ?e ?a ?v
    :in ?e
    :where
    [?e ?a ?v]])

(defrecord PersonRecord [id name])

(cr/defrule entities-from-records
  [?pr <- PersonRecord]
  =>
  (cr/insert! (f/->Datom -99 :person/id (:id ?pr))
              (f/->Datom -99 :person/name (:name ?pr))))

#?(:cljs (enable-console-print!))

(api/rulebase rulebase factui.impl.session-test)
(def base (api/session rulebase test-schema ::session))

(deftest basic-insertion
  (let [s (api/transact-all base [{:person/id 42
                                   :person/name "Luke"}])
        results (api/query s person-by-pid 42)]
    (is (= results
           #{[:person/name "Luke"]
             [:person/id 42]}))))

(deftest tempids-resolve-to-same-entity
  (let [[s bindings] (api/transact base [{:db/id -47
                                          :person/name "Luke"}
                                         {:db/id -47
                                          :person/id 42
                                          :person/age 32}])
        results (api/query s person-by-pid 42)
        eid (bindings -47)]
    (is (= results
          #{[:person/name "Luke"]
            [:person/age 32]
            [:person/id 42]}))))

(deftest tempids-as-ref-values
  (testing "same transaction"
    (let [[s bindings] (api/transact base [{:db/id -1
                                            :person/id 42
                                            :person/name "Luke"
                                            :person/friends -2}
                                           {:db/id -2
                                            :person/id 43
                                            :person/name "Joe"}])
          results (api/query s person-by-pid 42)]
      (is (= results
            #{[:person/name "Luke"]
              [:person/id 42]
              [:person/friends (bindings -2)]}))))
  (testing "upsert (same tx)"
    (let [[s bindings] (api/transact base [{:person/id 42
                                            :person/name "Luke"
                                            :person/friends {:person/id 43}}
                                           {:db/id -2
                                            :person/id 43
                                            :person/name "Joe"}])
          results (api/query s person-by-pid 42)]
      (is (= results
            #{[:person/name "Luke"]
              [:person/id 42]
              [:person/friends (bindings -2)]}))))
  (testing "upsert (different tx)"
    (let [[s bindings] (api/transact base [{:db/id -2
                                            :person/id 43
                                            :person/name "Joe"}])
          [s _] (api/transact s [{:person/id 42
                                  :person/name "Luke"
                                  :person/friends {:person/id 43}}])
          results (api/query s person-by-pid 42)]
      (is (= results
            #{[:person/name "Luke"]
              [:person/id 42]
              [:person/friends (bindings -2)]})))))

(deftest identity-attribute-resolution
  (testing "same transaction"
    (let [s (api/transact-all base [{:person/id 42
                                     :person/name "Luke"}
                                    {:person/id 42
                                     :person/age 32}])
          results (api/query s person-by-pid 42)]
      (is (= results
            #{[:person/name "Luke"]
              [:person/id 42]
              [:person/age 32]}))))
  (testing "different transactions"
    (let [s (api/transact-all base [{:person/id 42
                                      :person/name "Luke"}]
                                   [{:person/id 42
                                     :person/age 32}])
          results (api/query s person-by-pid 42)]
      (is (= results
            #{[:person/name "Luke"]
              [:person/id 42]
              [:person/age 32]})))))

(deftest no-duplicate-facts
  (let [s (api/transact-all base [{:person/id 42
                                   :person/name "Luke"}]
                                 [{:person/id 42
                                   :person/name "Luke"}])
        results (api/query-raw s person-by-pid 42)]
    (is (= 2 (count results)))))

(deftest card-many
  (let [s (api/transact-all base [{:person/id 42
                                   :person/likes #{"beer" "meat"}}]
                                  [{:person/id 42
                                    :person/likes #{"cheese"}}])
        results (api/query s person-by-pid 42)]
    (is (= results
          #{[:person/id 42]
            [:person/likes "beer"]
            [:person/likes "cheese"]
            [:person/likes "meat"]}))))

(deftest card-one
  (let [s (api/transact-all base [{:person/id 42
                                   :person/name "Luke"}]
                                 [{:person/id 42
                                  :person/name "Lukas"}])
        results (api/query s person-by-pid 42)]
    (is (= results
          #{[:person/name "Lukas"]
            [:person/id 42]}))))

(deftest basic-logic
  (let [[s bindings] (api/transact base [{:db/id -42
                                         :person/id 42
                                         :person/name "Popeye"}])
        results (api/query s person-by-pid 42)]
    (is (= results
          #{[:person/name "Popeye"]
            [:person/id 42]
            [:person/likes "spinach"]}))))

(deftest duplicate-entailed-facts
  (testing "implied followed by explicit (separate transactions)"
    (let [s (api/transact-all base [{:person/id 42
                                     :person/name "Popeye"}]
                                   [{:person/id 42
                                     :person/likes "spinach"}])
          results (api/query-raw s person-by-pid 42)]
      (is (= 3 (count results)))))
  (testing "implied and explicit in same transaction"
    (let [s (api/transact-all base [{:person/id 42
                                     :person/name "Popeye"
                                     :person/likes "spinach"}]
              [{:person/id 42
                :person/likes "spinach"}])
          results (api/query-raw s person-by-pid 42)]
      (is (= 3 (count results)))))
  (testing "explicit followed by implied"
    (let [s (api/transact-all base [{:person/id 42
                                     :person/likes "spinach"}]
                                   [{:person/id 42
                                     :person/name "Popeye"}])
          results (api/query-raw s person-by-pid 42)]
      (is (= 3 (count results))))))

(deftest simple-retraction
  (let [[s bindings] (api/transact base [{:db/id -99
                                          :person/id 42
                                          :person/name "Luke"
                                          :person/likes #{"vinegar"}}])
        eid (bindings -99)
        [s2 _] (api/transact s [[:db/retract eid :person/likes "vinegar"]])
        results1 (api/query s person-by-pid 42)
        results2 (api/query s2 person-by-pid 42)]
    (is (= results1
          #{[:person/name "Luke"]
            [:person/id 42]
            [:person/likes "vinegar"]}))
    (is (= results2
          #{[:person/name "Luke"]
            [:person/id 42]}))))

(deftest logical-retractions
  (let [s (api/transact-all base [{:person/id 42
                                   :person/name "Locutus"}])
        results (api/query s person-by-pid  42)]
    (is (= results
          #{[:person/name "Locutus"]
            [:person/id 42]
            [:person/likes "assimilation"]}))
    (let [s2 (api/transact-all s [{:person/id 42
                                   :person/name "Picard"}])
          results2 (api/query s2 person-by-pid 42)]
      (is (= results2
            #{[:person/name "Picard"]
              [:person/id 42]
              [:person/likes "tea"]})))))

(deftest non-datom-facts
  (let [s (cr/insert base (->PersonRecord 69 "Alex"))
        s (cr/fire-rules s)
        results (cr/query s person-by-pid :?pid 69)]
    (is (= (set (map #(select-keys % [:?a :?v]) results))
          #{{:?a :person/name :?v "Alex"}
            {:?a :person/id :?v 69}}))
    (testing "duplicate prevention"
      (let [s (api/transact-all s [{:person/id 69
                                    :person/name "Alex"}])
            results (cr/query s person-by-pid :?pid 69)]
        (is (= 2 (count (set results))))))
    (testing "overwriting card-one"
      (let [s (api/transact-all s [{:person/id 69
                                    :person/name "Alejandro"}])
            results (cr/query s person-by-pid :?pid 69)]
        (is (= (set (map #(select-keys % [:?a :?v]) results))
              #{{:?a :person/name :?v "Alejandro"}
                {:?a :person/id :?v 69}}))))))

#_(deftest retract-entity
  (let [[s1 bindings] (api/transact base [{:db/id -42
                                           :person/id 42
                                           :person/name "Luke"
                                           :person/likes ["Cheese" "Beer"]}])
        eid (bindings -42)
        r1 (api/query s1 all-attrs eid)]
    (is (= r1 #{[eid :person/id 42]
                [eid :person/name "Luke"]
                [eid :person/likes "Cheese"]
                [eid :person/likes "Beer"]}))
    (let [s2 (api/transact-all s1 [[:db.fn/retractEntity eid]])
          r2 (api/query s2 all-attrs eid)]
      (is (= nil r2)))))

  ; Known problematic scenario:
  ;
  ; 1. A logical rule R adds a datom X
  ; 2. X is explicitly asserted
  ; 3. The logical rule R is retracted. Datom X will also be retracted.

  ; This is slightly sub-optimal, but livable. Just don't do that.
  ;
  ; A worse variant is:
  ;
  ; 1. A (card one) datom X is explicitly asserted
  ; 2. A logical rule R overwrites Datom X with Datom Y
  ; 3. Rule R becomes untrue, retracting Datom Y
  ; 4. Datom X is still gone.

  ; Again, I think this is livable (if suboptimal).

  ; Fixing it would require storing (and re-asserting) historical facts,
  ; in the event of logical retraction.

  ; The rule of thumb is pretty simple: never explicitly assert a fact
  ; that can also be logically asserted.


;;; TODO: Make sure that a rule that overwrites the result of another rule in the same session applies in the proper order.





