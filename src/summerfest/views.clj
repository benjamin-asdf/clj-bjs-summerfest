(ns summerfest.views
  (:require [hiccup.core :as h]
            [hiccup.page :as page]
            [clojure.string :as str]
            [summerfest.db :as db]
            [summerfest.i18n :refer [t]]
            [summerfest.config :refer [u base-path]]))

(defn- js-string-escape
  "Escape a string for embedding inside a single-quoted JS literal in data-signals."
  [s]
  (-> (or s "")
      (str/replace "\\" "\\\\")
      (str/replace "'" "\\'")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")))

(defn nav-user-html
  "Clickable navbar greeting that opens the display-name modal."
  [locale user]
  (h/html
   [:span#nav-user.nav-user
    {:role "button"
     :tabindex "0"
     :title (t locale :profile/edit-name-hint)
     "data-on:click" "$showNameEdit = true"}
    (t locale :nav/hi (db/effective-name user))
    [:span.nav-user-edit-icon {:aria-hidden "true"} " ✎"]]))

(defn- name-modal-html [locale user]
  (h/html
   [:div.modal-overlay {"data-class" "{open: $showNameEdit}"
                        "data-on:click" "$showNameEdit = false"}
    [:div.modal {"data-on:click" "evt.stopPropagation()"}
     [:h3 (t locale :profile/edit-name)]
     [:input.modal-input
      {:type "text"
       "data-bind" "newDisplayName"
       :placeholder (t locale :profile/name-placeholder)
       :maxlength "30"
       "data-on:keydown" (str "evt.key === 'Enter' && @post('"
                              (u "/api/profile/display-name") "')")}]
     [:div.modal-actions
      [:button.btn.btn-no
       {"data-on:click" "$showNameEdit = false"}
       (t locale :profile/cancel)]
      [:button.btn.btn-save
       {"data-on:click" (str "@post('" (u "/api/profile/display-name") "')")}
       (t locale :profile/save)]]]]))

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
      [:link {:rel "stylesheet" :href (u "/style.css")}]
      [:link {:rel "icon" :href "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>S</text></svg>"}]
      [:script (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";")]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/gh/starfederation/datastar/bundles/datastar.js"}]]
     [:body
      (when user
        {:data-signals (str "{showNameEdit:false,newDisplayName:'"
                            (js-string-escape (db/effective-name user))
                            "'}")})
      [:nav.navbar
       [:a.nav-brand {:href (u "/")} (t locale :nav/brand)]
       [:input#nav-toggle {:type "checkbox"}]
       [:label.nav-toggle {:for "nav-toggle"} "☰"]
       [:div.nav-links
        [:a {:href (u "/")} (t locale :nav/home)]
        ;; [:a {:href (u "/contact")} (t locale :nav/contact)]
        ;; [:a {:href (u "/directions")} (t locale :nav/directions)]
        [:a {:href (u "/gallery")} (t locale :nav/gallery)]
        [:a {:href (u "/chat")} (t locale :nav/chat)]
        [:a {:href (u "/party")} (t locale :nav/party)]
        (when user
          (nav-user-html locale user))
        [:a.lang-switch {:href (u (str "/set-locale?locale=" (name other-locale)))} other-label]]]
      [:main.container body]
      (when user (name-modal-html locale user))
      [:footer
       [:p [:a {:href (u "/impressum")} (t locale :footer/text)]]]])))

;; --- Party game layout (fullscreen, no nav) ---

(defn party-layout
  "Minimal fullscreen layout for party game pages."
  [title locale mode & body]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, user-scalable=no"}]
    [:title (str title " | " (t locale :nav/brand))]
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";")]]
   [:body {:data-party-mode mode} body]))

(defn login-page [locale]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (t locale :login/title)]
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";")]]
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
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";")]]
   [:body
    [:main.container.center
     [:div.card.login-card
      [:h1 (t locale :login/invalid-title)]
      [:p (t locale :login/invalid-text)]
      [:p.small (t locale :login/invalid-hint)]]]]))

;; --- RSVP ---

(defn rsvp-fragment [locale user rsvp]
  (let [group? (> (or (:group-size user) 2) 1)
        yes-class (str "btn btn-yes" (when (and rsvp (:attending rsvp)) " active"))
        no-class (str "btn btn-no" (when (and rsvp (not (:attending rsvp))) " active"))]
    (h/html
     [:div#rsvp-form.card
      [:h2 (t locale :rsvp/heading)]
      (when rsvp
        [:div.rsvp-status
         (if (:attending rsvp)
           [:p.status-yes (t locale (if group? :rsvp/status-yes-group :rsvp/status-yes-solo))]
           [:p.status-no (t locale :rsvp/status-no)])])
      [:div.rsvp-buttons
       [:button {:class yes-class
                 "data-on:click" (str "$attending = true; @post('" (u "/api/rsvp") "')")}
        (t locale (if group? :rsvp/we-coming :rsvp/i-coming))]
       [:button {:class no-class
                 "data-on:click" (str "$attending = false; @post('" (u "/api/rsvp") "')")}
        (t locale (if group? :rsvp/we-not-coming :rsvp/i-not-coming))]]
      [:div.info-section
       [:label {:for "additional-info"} (t locale :rsvp/info-label)]
       [:textarea#additional-info
        {"data-bind" "additionalInfo"
         :placeholder (t locale :rsvp/info-placeholder)
         :rows "3"}
        (or (:additional-info rsvp) "")]
       [:button.btn.btn-save
        {"data-on:click" (str "@post('" (u "/api/rsvp/info") "')")}
        (t locale :rsvp/save)]]])))

(defn home-page [ctx rsvp]
  (let [{:keys [user locale]} ctx]
    (layout (t locale :nav/home) ctx
            [:div.hero
             [:h1 (t locale :home/title)]
             [:p.subtitle (t locale :home/subtitle (db/effective-name user))]]
            [:div {:id "rsvp-section"
                   :data-signals (str "{attending: "
                                      (if rsvp (str (:attending rsvp)) "null")
                                      ", additionalInfo: '"
                                      (-> (or (:additional-info rsvp) "")
                                          (.replace "'" "\\'"))
                                      "'}")}
             (rsvp-fragment locale user rsvp)])))

;; --- Impressum ---

(defn impressum-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :impressum/title) ctx
            [:div.card
             [:h1 (t locale :impressum/title)]
             [:h3 (t locale :impressum/responsible)]
             [:p (t locale :impressum/name) [:br]
              (t locale :impressum/address)]
             [:h3 (t locale :impressum/contact)]
             [:p (t locale :contact/email) ": "
              [:a {:href "mailto:fest@example.com"} "fest@example.com"]]
             [:p.small (t locale :impressum/disclaimer)]])))

;; --- Contact ---

(defn contact-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :contact/title) ctx
            [:div.card
             [:h1 (t locale :contact/title)]
             [:p (t locale :contact/intro)]
             [:div.contact-info
              [:p (t locale :contact/email) ": " [:a {:href "mailto:fest@example.com"} "fest@example.com"]]
              [:p (t locale :contact/phone) ": " [:a {:href "tel:+491234567890"} "+49 123 456 7890"]]
              [:p (t locale :contact/or-chat) [:a {:href (u "/chat")} (t locale :contact/chat-link)] "!"]]])))

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
         [:img {:src (u (str "/thumbs/" (:filename photo)))
                "data-full" (u (str "/uploads/" (:filename photo)))
                :alt (or (:original-name photo) "Photo")
                :loading "lazy"
                :decoding "async"}]
         [:p.photo-meta (t locale :gallery/uploaded-by (:user-name photo))]])
      [:p.empty (t locale :gallery/empty)])]))

(defn gallery-page [ctx photos full?]
  (let [locale (:locale ctx)]
    (layout (t locale :gallery/title) ctx
            [:div.card
             [:h1 (t locale :gallery/title)]
             (if full?
               [:p.empty (t locale :gallery/full)]
               [:form.upload-form {:id "upload-form"
                                   :action (u "/api/photo")
                                   :method "post"
                                   :enctype "multipart/form-data"}
                [:label.btn.btn-save.upload-trigger {:for "photo-file"}
                 (t locale :gallery/upload)]
                [:input#photo-file
                 {:type "file" :name "photo" :accept "image/*" :required true
                  :hidden true
                  :onchange "this.form.submit()"}]])]
            (photo-grid locale photos)
            [:div#lightbox.lightbox {:role "dialog" :aria-modal "true" :hidden true}
             [:button.lightbox-close {:type "button" :aria-label (t locale :gallery/close)} "×"]
             [:button.lightbox-prev {:type "button" :aria-label (t locale :gallery/prev)} "‹"]
             [:img#lightbox-img.lightbox-img {:alt ""}]
             [:button.lightbox-next {:type "button" :aria-label (t locale :gallery/next)} "›"]]
            [:script "
(function(){
  var box=document.getElementById('lightbox'),img=document.getElementById('lightbox-img');
  if(!box||!img)return;
  var grid=document.getElementById('photo-grid'),idx=-1,srcs=[];
  function refresh(){srcs=Array.from(grid.querySelectorAll('.photo-card img')).map(function(i){return i.dataset.full||i.currentSrc||i.src})}
  function show(i){if(!srcs.length)return;idx=(i+srcs.length)%srcs.length;img.src=srcs[idx]}
  function open(i){refresh();show(i);box.hidden=false;document.body.style.overflow='hidden'}
  function close(){box.hidden=true;img.removeAttribute('src');document.body.style.overflow=''}
  if(grid){grid.addEventListener('click',function(e){
    var card=e.target.closest('.photo-card');if(!card)return;
    var cards=Array.from(grid.querySelectorAll('.photo-card'));
    var i=cards.indexOf(card);if(i>=0)open(i)
  })}
  box.querySelector('.lightbox-close').addEventListener('click',close);
  box.querySelector('.lightbox-prev').addEventListener('click',function(e){e.stopPropagation();show(idx-1)});
  box.querySelector('.lightbox-next').addEventListener('click',function(e){e.stopPropagation();show(idx+1)});
  box.addEventListener('click',function(e){if(e.target===box)close()});
  img.addEventListener('click',function(e){
    e.stopPropagation();
    var fn=img.requestFullscreen||img.webkitRequestFullscreen;
    var p=fn&&fn.call(img);
    if(!p)window.open(img.src,'_blank','noopener');
    else if(p.catch)p.catch(function(){window.open(img.src,'_blank','noopener')})
  });
  document.addEventListener('keydown',function(e){
    if(box.hidden)return;
    if(e.key==='Escape')close();
    else if(e.key==='ArrowLeft')show(idx-1);
    else if(e.key==='ArrowRight')show(idx+1)
  });
})();
"])))

;; --- Chat ---

(def ^:private time-fmt (java.time.format.DateTimeFormatter/ofPattern "HH:mm"))

(defn- linkify
  "Split `s` into a seq of plain strings and hiccup anchor tags for any https URLs."
  [s]
  (let [s (or s "")
        m (re-matcher #"https://[^\s<>\"]+" s)]
    (loop [parts []
           last-end 0]
      (if (.find m)
        (let [start (.start m)
              end (.end m)
              raw (.group m)
              trimmed (str/replace raw #"[.,;:!?)\]}]+$" "")
              tail (subs raw (count trimmed))]
          (recur (cond-> parts
                   (< last-end start) (conj (subs s last-end start))
                   :always (conj [:a.chat-link {:href trimmed
                                                :target "_blank"
                                                :rel "noopener noreferrer"}
                                  trimmed])
                   (seq tail) (conj tail))
                 end))
        (seq (cond-> parts
               (< last-end (count s)) (conj (subs s last-end))))))))

(defn- msg-html [locale msg current-user-id]
  (let [own? (= (:user-id msg) current-user-id)
        liked? (:liked-by-me msg)
        pinned? (:pinned msg)
        like-count (or (:like-count msg) 0)]
    [:div.chat-msg {:id (str "msg-" (:id msg))
                    :class (str (when own? "own") (when pinned? " pinned"))}
     [:div.chat-msg-top
      [:span.chat-author (:user-name msg)]
      [:span.chat-time (.format time-fmt (.toLocalDateTime (:created-at msg)))]]
     [:span.chat-text (linkify (:message msg))]
     [:div.chat-msg-actions
      [:button.chat-action {:class (when liked? "liked")
                            "data-on:click" (str "@post('" (u "/api/chat/like") "?id=" (:id msg) "')")}
       [:span.action-icon (if liked? "♥" "♡")]
       (when (pos? like-count) [:span.action-count (str like-count)])]
      [:button.chat-action {:class (when pinned? "is-pinned")
                            "data-on:click" (if pinned?
                                              (str "confirm('" (t locale :chat/confirm-unpin) "') && @post('"
                                                   (u "/api/chat/pin") "?id=" (:id msg) "')")
                                              (str "@post('" (u "/api/chat/pin") "?id=" (:id msg) "')"))}
       [:span.action-icon (if pinned? "📌" "pin")]]]]))

(defn single-msg-html
  "Render a single message as an HTML string."
  [locale msg current-user-id]
  (h/html (msg-html locale msg current-user-id)))

(defn load-older-html
  "Render the load-older wrapper. Always emits the wrapper so morph patches
   can replace its contents (button or empty)."
  [locale oldest-id more?]
  (h/html
   [:div#load-older-wrap.load-older-wrap
    (when (and more? oldest-id)
      [:button#load-older.btn.btn-link
       {"data-on:click" (str "@get('" (u (str "/api/chat/older?before=" oldest-id)) "')")}
       (t locale :chat/load-older)])]))

(defn chat-messages-html [locale messages current-user-id]
  (h/html
   [:div#chat-messages.chat-messages
    (if (seq messages)
      (for [msg messages]
        (msg-html locale msg current-user-id))
      [:p#chat-empty.empty (t locale :chat/empty)])]))

(defn pinned-messages-html [locale pinned-messages current-user-id]
  (h/html
   [:div#pinned-messages.pinned-messages
    [:h3 (t locale :chat/pinned)]
    (if (seq pinned-messages)
      (for [msg pinned-messages]
        [:div.pinned-item {:onclick (str "document.getElementById('msg-" (:id msg) "')?.scrollIntoView({behavior:'smooth',block:'center'})")}
         [:span.pinned-author (:user-name msg)]
         [:span.pinned-text (linkify (:message msg))]])
      [:p.empty.small (t locale :chat/no-pins)])]))

(defn chat-page [ctx messages pinned-messages oldest-id more-older?]
  (let [{:keys [user locale]} ctx]
    (layout (t locale :chat/title) ctx
            [:div.chat-layout {"data-init" (str "@get('" (u "/api/chat/stream") "')")}
             [:div.card.chat-card {:data-signals "{chatMsg: ''}"}
              [:h1 (t locale :chat/title)]
              (load-older-html locale oldest-id more-older?)
              (chat-messages-html locale messages (:id user))
              [:button#scroll-btn.scroll-to-bottom (t locale :chat/jump-to-latest)]
              [:div.chat-input
               [:input {:type "text"
                        "data-bind" "chatMsg"
                        :placeholder (t locale :chat/placeholder)
                        "data-on:keydown" (str "evt.key === 'Enter' && @post('" (u "/api/chat/send") "')")}]
               [:button.btn.btn-send {"data-on:click" (str "@post('" (u "/api/chat/send") "')")} (t locale :chat/send)]]]
             [:div.card.pinned-sidebar
              (pinned-messages-html locale pinned-messages (:id user))]]
            [:script "
(function(){
  var m=document.getElementById('chat-messages'),b=document.getElementById('scroll-btn'),us=false;
  if(!m||!b)return;
  m.scrollTop=m.scrollHeight;
  m.addEventListener('scroll',function(){
    var ab=m.scrollHeight-m.scrollTop-m.clientHeight<60;
    us=!ab;b.classList.toggle('visible',us);
  });
  b.addEventListener('click',function(){m.scrollTo({top:m.scrollHeight,behavior:'smooth'})});
  new MutationObserver(function(){if(!us){m.scrollTop=m.scrollHeight}}).observe(m,{childList:true,subtree:true});
})();
"])))

;; --- Party Games ---

(defn party-hub-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :party/title) ctx
            [:div.hero
             [:h1 (t locale :party/title)]
             [:p.subtitle (t locale :party/subtitle)]]
            [:div.party-grid
             [:a.card.party-card {:href (u "/party/bump")}
              [:div.party-icon "💥"]
              [:h2 (t locale :party/bump-title)]
              [:p (t locale :party/bump-desc)]]
             [:a.card.party-card {:href (u "/party/room")}
              [:div.party-icon "📱"]
              [:h2 (t locale :party/room-title)]
              [:p (t locale :party/room-desc)]]
             [:a.card.party-card {:href (u "/party/radar")}
              [:div.party-icon "🧭"]
              [:h2 (t locale :party/radar-title)]
              [:p (t locale :party/radar-desc)]]])))

(defn- party-game-page
  "Shared structure for all three party game modes."
  [locale mode mode-title-key hint-key]
  (party-layout (t locale mode-title-key) locale mode
                [:div.party-page
                 ;; Lobby — just name + join
                 [:div#party-lobby.party-lobby
                  [:a.party-back {:href (u "/party")} (str "← " (t locale :party/back))]
                  [:h1 (t locale mode-title-key)]
                  [:p.party-hint (t locale hint-key)]
                  [:div.party-form
                   [:input#player-name {:type "text"
                                        :placeholder (t locale :party/your-name)
                                        :autocomplete "given-name"
                                        :maxlength "20"}]
                   [:button.btn.btn-yes {:onclick "partyJoin()"
                                         :style "width:100%"}
                    (t locale :party/join)]]]
                 ;; Game UI
                 [:div#party-game.party-game {:style "display:none"}
                  [:canvas#party-canvas]
                  [:div.party-overlay
                   [:div.party-info
                    [:span (str (t locale :party/players) ": ")]
                    [:span#player-count "0"]]
                   [:div#party-chain.party-chain]]]
                 [:script {:src (u "/party.js")}]]))

(defn party-bump-page [locale]
  (party-game-page locale "bump" :party/bump-title :party/bump-hint))

(defn party-room-page [locale]
  (party-game-page locale "room" :party/room-title :party/room-hint))

(defn party-radar-page [locale]
  (party-game-page locale "radar" :party/radar-title :party/radar-hint))
