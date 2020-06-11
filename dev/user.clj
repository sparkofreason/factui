(ns user
  (:require [factui.api :as f]))

(alter-var-root #'*print-namespace-maps* (constantly false))

(def schema {:env/service {:db/cardinality :db.cardinality/many
                           :db/valueType :db.type/ref}
             :cron_jobs {:db/cardinality :db.cardinality/many
                         :db/valueType :db.type/ref}
             :jobs {:db/cardinality :db.cardinality/many
                    :db/valueType :db.type/ref}
             :components {:db/cardinality :db.cardinality/many
                          :db/valueType :db.type/ref}
             :container_meta {:db/cardinality :db.cardinality/many
                              :db/valueType :db.type/ref}
             :zones {:db/cardinality :db.cardinality/many}
             :team {:db/valueType :db.type/ref}
             :deployment {:db/valueType :db.type/ref}
             :blue_green {:db/valueType :db.type/ref}
             :blue {:db/valueType :db.type/ref}
             :green {:db/valueType :db.type/ref}
             :no_weight {:db/valueType :db.type/ref}
             :helm_chart_default_values_files {:db/cardinality :db.cardinality/many}
             :helm_chart_production_overrides_values_files {:db/cardinality :db.cardinality/many}
             :status_details {:db/valueType :db.type/ref}
             :current {:db/valueType :db.type/ref}
             :replicas {:db/valueType :db.type/ref}})

(f/defrule bar
  [?e :service/id ?id]
  =>
  (println ?e ?id))

(f/rulebase foo user)
(def s (f/session foo schema))
(f/transact s [{:env/service [{:service/id "foo"}
                              {:service/id "bar"}]}])
