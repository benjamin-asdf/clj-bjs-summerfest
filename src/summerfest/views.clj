(ns summerfest.views
  (:require [hiccup.core :as h]
            [hiccup.page :as page]
            [summerfest.i18n :refer [t]]))

(defn layout
  "Base page layout with navbar. ctx is {:user ... :locale ...}."
  [title {:keys [user locale] :or {locale :de}} & body]
  (let [other-locale (if (= locale :de) :en :de)
        other-label (if (= locale :de) "EN" "DE")]
    (page/html5
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (str title " | " (t locale :nav/brand))]
      [:link {:rel "stylesheet" :href "/style.css"}]
      [:link {:rel "icon" :href "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🌻</text></svg>"}]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/npm/@starfederation/datastar"}]]
     [:body
      [:nav.navbar
       [:a.nav-brand {:href "/"} (t locale :nav/brand)]
       [:input#nav-toggle {:type "checkbox"}]
       [:label.nav-toggle {:for "nav-toggle"} "☰"]
       [:div.nav-links
        [:a {:href "/"} (t locale :nav/home)]
        [:a {:href "/contact"} (t locale :nav/contact)]
        [:a {:href "/directions"} (t locale :nav/directions)]
        [:a {:href "/gallery"} (t locale :nav/gallery)]
        [:a {:href "/chat"} (t locale :nav/chat)]
        (when user
          [:span.nav-user (t locale :nav/hi (:name user))])
        [:a.lang-switch {:href (str "/set-locale?locale=" (name other-locale))} other-label]]]
      [:main.container body]
      [:footer
       [:p (t locale :footer/text)]]])))

(defn login-page [locale]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (t locale :login/title)]
    [:link {:rel "stylesheet" :href "/style.css"}]]
   [:body
    [:main.container.center
     [:div.card.login-card
      [:h1 (t locale :login/title)]
      [:p (t locale :login/use-link)]
      [:p.small (t locale :login/lost-link)]]]]))

(defn invalid-token-page [locale]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (t locale :login/invalid-title)]
    [:link {:rel "stylesheet" :href "/style.css"}]]
   [:body
    [:main.container.center
     [:div.card.login-card
      [:h1 (t locale :login/invalid-title)]
      [:p (t locale :login/invalid-text)]
      [:p.small (t locale :login/invalid-hint)]]]]))

;; --- RSVP ---

(defn rsvp-fragment [locale user rsvp]
  (let [group? (> (or (:group-size user) 2) 1)]
    (h/html
     [:div#rsvp-form.card
      [:h2 (t locale :rsvp/heading)]
      (when rsvp
        [:div.rsvp-status
         (if (:attending rsvp)
           [:p.status-yes (t locale (if group? :rsvp/status-yes-group :rsvp/status-yes-solo))]
           [:p.status-no (t locale :rsvp/status-no)])])
      [:div.rsvp-buttons
       [:button.btn.btn-yes
        {:data-on-click (str "$attending = true; @post('/api/rsvp')")
         :class (when (and rsvp (:attending rsvp)) "active")}
        (t locale (if group? :rsvp/we-coming :rsvp/i-coming))]
       [:button.btn.btn-no
        {:data-on-click (str "$attending = false; @post('/api/rsvp')")
         :class (when (and rsvp (not (:attending rsvp))) "active")}
        (t locale (if group? :rsvp/we-not-coming :rsvp/i-not-coming))]]
      [:div.info-section
       [:label {:for "additional-info"} (t locale :rsvp/info-label)]
       [:textarea#additional-info
        {:data-model "additionalInfo"
         :placeholder (t locale :rsvp/info-placeholder)
         :rows "3"}
        (or (:additional-info rsvp) "")]
       [:button.btn.btn-save
        {:data-on-click "@post('/api/rsvp/info')"}
        (t locale :rsvp/save)]]])))

(defn home-page [ctx rsvp]
  (let [{:keys [user locale]} ctx]
    (layout (t locale :nav/home) ctx
            [:div.hero
             [:h1 (t locale :home/title)]
             [:p.subtitle (t locale :home/subtitle (:name user))]]
            [:div {:id "rsvp-section"
                   :data-signals (str "{attending: "
                                      (if rsvp (str (:attending rsvp)) "null")
                                      ", additionalInfo: '"
                                      (-> (or (:additional-info rsvp) "")
                                          (.replace "'" "\\'"))
                                      "'}")}
             (rsvp-fragment locale user rsvp)])))

;; --- Contact ---

(defn contact-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :contact/title) ctx
            [:div.card
             [:h1 (t locale :contact/title)]
             [:p (t locale :contact/intro)]
             [:div.contact-info
              [:p "📧 " (t locale :contact/email) ": " [:a {:href "mailto:fest@example.com"} "fest@example.com"]]
              [:p "📱 " (t locale :contact/phone) ": " [:a {:href "tel:+491234567890"} "+49 123 456 7890"]]
              [:p (t locale :contact/or-chat) [:a {:href "/chat"} (t locale :contact/chat-link)] "!"]]])))

;; --- Directions ---

(defn directions-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :directions/title) ctx
            [:div.card
             [:h1 (t locale :directions/title)]
             [:h3 (t locale :directions/address)]
             [:p "Summer Fest Venue" [:br] "123 Garden Lane" [:br] "12345 Sunville"]
             [:h3 (t locale :directions/by-car)]
             [:p (t locale :directions/by-car-text)]
             [:h3 (t locale :directions/by-transit)]
             [:p (t locale :directions/by-transit-text)]
             [:h3 (t locale :directions/map)]
             [:div.map-placeholder
              [:p (t locale :directions/map-placeholder)]
              [:p.small "Coordinates: 52.520, 13.405"]]])))

;; --- Gallery ---

(defn photo-grid [locale photos]
  (h/html
   [:div#photo-grid.photo-grid
    (if (seq photos)
      (for [photo photos]
        [:div.photo-card
         [:img {:src (str "/uploads/" (:filename photo))
                :alt (or (:original-name photo) "Photo")
                :loading "lazy"}]
         [:p.photo-meta (str (:user-name photo))]])
      [:p.empty (t locale :gallery/empty)])]))

(defn gallery-page [ctx photos]
  (let [locale (:locale ctx)]
    (layout (t locale :gallery/title) ctx
            [:div.card
             [:h1 (t locale :gallery/title)]
             [:form.upload-form {:id "upload-form"
                                 :action "/api/photo"
                                 :method "post"
                                 :enctype "multipart/form-data"}
              [:label.file-label {:for "photo-file"} (t locale :gallery/choose)]
              [:input#photo-file {:type "file" :name "photo" :accept "image/*"}]
              [:button.btn.btn-save {:type "submit"} (t locale :gallery/upload)]]]
            (photo-grid locale photos))))

;; --- Chat ---

(defn chat-messages-html [locale messages current-user-id]
  (h/html
   [:div#chat-messages.chat-messages
    (if (seq messages)
      (for [msg messages]
        [:div.chat-msg {:class (when (= (:user-id msg) current-user-id) "own")}
         [:span.chat-author (:user-name msg)]
         [:span.chat-text (:message msg)]
         [:span.chat-time (.format (java.time.format.DateTimeFormatter/ofPattern "HH:mm")
                                   (.toLocalDateTime (:created-at msg)))]])
      [:p.empty (t locale :chat/empty)])]))

(defn chat-page [ctx messages]
  (let [{:keys [user locale]} ctx]
    (layout (t locale :chat/title) ctx
            [:div.card.chat-card
             [:h1 (t locale :chat/title)]
             [:div {:id "chat-container"
                    :data-signals "{chatMsg: ''}"}
              (chat-messages-html locale messages (:id user))
              [:div.chat-input
               [:input {:type "text"
                        :data-model "chatMsg"
                        :placeholder (t locale :chat/placeholder)
                        :data-on-keydown.enter "@post('/api/chat/send')"}]
               [:button.btn.btn-send {:data-on-click "@post('/api/chat/send')"} (t locale :chat/send)]]
              [:script "setInterval(async()=>{try{const r=await fetch('/api/chat/messages-html');if(r.ok){document.getElementById('chat-messages').outerHTML=await r.text()}}catch(e){}},5000);"]]])))
