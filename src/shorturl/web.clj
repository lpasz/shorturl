(ns shorturl.web
  (:require [reitit.ring :as ring]
            [reitit.core :as r]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]
            [shorturl.core.redirects :as redirects]))

(def router-config
  {:conflicts nil
   :exception pretty/exception
   :data {:coercion reitit.coercion.spec/coercion
          :muuntaja m/instance
          :middleware [parameters/parameters-middleware
                       muuntaja/format-negotiate-middleware
                       muuntaja/format-response-middleware
                       (exception/create-exception-middleware
                        {::exception/default (partial exception/wrap-log-to-console
                                                      exception/default-handler)})
                       muuntaja/format-request-middleware
                       coercion/coerce-response-middleware
                       coercion/coerce-request-middleware
                       multipart/multipart-middleware]}})

(def router (ring/router
             [["/api/shorty"
               {:post {:summary "create a new slug for redirecting the url"
                       :parameters {:body {:url string?}}
                       :responses {201 {:body {:slug string?}}}
                       :handler (fn [{{{:keys [url]} :body} :parameters}]
                                  (resp/created
                                   "/api/shorty"
                                   {:slug (redirects/create-short-link url)}))}}]

              ["/:slug"
               {:get {:summary "given a valid slug redirects the user to url"
                      :parameters {:path-params {:slug string?}}
                      :responses {302 {}
                                  404 {}}
                      :handler (fn [{{:keys [slug]} :path-params}]
                                 (if-let [url (redirects/get-url-by-slug slug)]
                                   (resp/redirect (str "http://" url))
                                   (resp/not-found {})))}}]]
             router-config))

(def app (ring/ring-handler router))

(defn start []
  (jetty/run-jetty #'app {:port 8080, :join? false}))

(start)

(comment
  (r/match-by-path router "/api/shorty")
  (r/match-by-path router "/1")
  (app {:request-method :get :uri "/1"}))


