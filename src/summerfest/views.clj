(ns summerfest.views
  (:require [hiccup2.core :as h]
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

(defn- html5-page
  "Render an HTML5 document. Auto-escapes strings (hiccup2)."
  [& body]
  (str "<!DOCTYPE html>\n"
       (h/html (into [:html] body))))

(defn- save-btn-text-expr
  "JS expression for reactive save-button label: saved > saving > default."
  [locale saving-signal saved-signal]
  (str "$" saved-signal " ? '" (js-string-escape (t locale :ui/saved))
       "' : ($" saving-signal " ? '" (js-string-escape (t locale :ui/saving))
       "' : '" (js-string-escape (t locale :profile/save)) "')"))

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
       {:data-indicator "savingDisplayName"
        "data-attr-disabled" "$savingDisplayName"
        "data-text" (save-btn-text-expr locale "savingDisplayName" "savedDisplayName")
        "data-on:click" (str "@post('" (u "/api/profile/display-name") "')")}
       (t locale :profile/save)]]]]))

(defn layout
  "Base page layout with navbar. ctx is {:user ... :locale ...}."
  [title {:keys [user locale] :or {locale :de}} & body]
  (let [other-locale (if (= locale :de) :en :de)
        other-label (if (= locale :de) "EN" "DE")]
    (html5-page
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (str title " | " (t locale :nav/brand))]
      [:link {:rel "stylesheet" :href (u "/style.css")}]
      [:link {:rel "icon" :href "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>S</text></svg>"}]
      [:script (h/raw (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";"))]
      [:script {:type "module" :src "https://cdn.jsdelivr.net/gh/starfederation/datastar/bundles/datastar.js"}]]
     [:body
      (when user
        {:data-signals (str "{showNameEdit:false,newDisplayName:'"
                            (js-string-escape (db/effective-name user))
                            "',"
                            "savingDisplayName:false,savedDisplayName:false,"
                            "savingSecondaryName:false,savedSecondaryName:false,"
                            "savingInfo:false,savedInfo:false}")
         "data-effect"
         (str "$savedDisplayName && setTimeout(() => $savedDisplayName = false, 1500);"
              "$savedSecondaryName && setTimeout(() => $savedSecondaryName = false, 1500);"
              "$savedInfo && setTimeout(() => $savedInfo = false, 2000)")})
      [:nav.navbar
       [:a.nav-brand {:href (u "/")} (t locale :nav/brand)]
       [:input#nav-toggle {:type "checkbox"}]
       [:label.nav-toggle {:for "nav-toggle"} "☰"]
       [:div.nav-links
        [:a {:href (u "/")} (t locale :nav/home)]
        ;; [:a {:href (u "/contact")} (t locale :nav/contact)]
        [:a {:href (u "/gallery")} (t locale :nav/gallery)]
        [:a {:href (u "/chat")} (t locale :nav/chat)]
        ;; [:a {:href (u "/party")} (t locale :nav/party)]
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
  (html5-page
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1, user-scalable=no"}]
    [:title (str title " | " (t locale :nav/brand))]
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (h/raw (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";"))]]
   [:body {:data-party-mode mode} body]))

(defn login-page [locale]
  (html5-page
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (t locale :login/title)]
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (h/raw (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";"))]]
   [:body
    [:main.container.center
     [:div.card.login-card
      [:h1 (t locale :login/title)]
      [:p (t locale :login/use-link)]
      [:p.small (t locale :login/lost-link)]]]]))

(defn invalid-token-page [locale]
  (html5-page
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (t locale :login/invalid-title)]
    [:link {:rel "stylesheet" :href (u "/style.css")}]
    [:script (h/raw (str "window.SUMMERFEST_BASE=" (pr-str base-path) ";"))]]
   [:body
    [:main.container.center
     [:div.card.login-card
      [:h1 (t locale :login/invalid-title)]
      [:p (t locale :login/invalid-text)]
      [:p.small (t locale :login/invalid-hint)]]]]))

;; --- RSVP ---

(defn- rsvp-button
  "Render one RSVP choice button. `value` is the attending text written to the
   server; `current` is the user's current attending value, used only for the
   pre-hydration class so the initial paint matches. After hydration the
   active highlight is driven entirely by the `$attending` signal so the click
   feels instant — the POST still runs (in the background) to persist, mirror
   to the sheet, and (for yes_plus_one) mint the +1 link."
  [locale current value extra-class i18n-key]
  (let [active? (= current value)
        klass (str "btn " extra-class (when active? " active"))]
    [:button {:class klass
              "data-class:active" (str "$attending === '" value "'")
              "data-on:click" (str "$attending = '" value "'; @post('" (u "/api/rsvp") "')")}
     (t locale i18n-key)]))

(defn- secondary-link-panel
  "Panel shown to the primary user when they've selected 'wir kommen zu zweit'.
   Holds the +1's magic-link, copy/share affordances, and a display-name field
   for the primary to fill in their +1's name. Setting the name is optional —
   the link works either way."
  [locale {:keys [token-url]}]
  (h/html
   [:div.secondary-panel
    [:p.secondary-intro (t locale :rsvp/plus-one-intro)]
    [:div.secondary-link-row
     [:input.secondary-link-input
      {:type "text"
       :readonly true
       :value token-url
       :onclick "this.select()"}]
     [:button.btn.btn-copy
      {:type "button"
       :data-url token-url
       :data-copied-label (t locale :share/copied)
       :onclick (str "(function(b){"
                     "navigator.clipboard&&navigator.clipboard.writeText(b.dataset.url);"
                     "var o=b.textContent;b.textContent=b.dataset.copiedLabel;"
                     "setTimeout(function(){b.textContent=o},1500);"
                     "})(this)")}
      (t locale :share/copy)]
     [:button.btn.btn-share
      {:type "button"
       :data-url token-url
       :data-share-title (t locale :share/title)
       :data-share-text (t locale :rsvp/plus-one-share-text)
       :onclick (str "(function(b){"
                     "if(navigator.share){"
                     "navigator.share({title:b.dataset.shareTitle,text:b.dataset.shareText,url:b.dataset.url})"
                     ".catch(function(){});"
                     "}else{"
                     "navigator.clipboard&&navigator.clipboard.writeText(b.dataset.url);"
                     "alert(b.dataset.shareText+'\\n'+b.dataset.url)"
                     "}})(this)")}
      (t locale :share/share)]]
    [:p.secondary-note (t locale :rsvp/plus-one-note)]
    [:div.secondary-name-section
     [:label.secondary-name-label {:for "secondary-display-name"}
      (t locale :rsvp/plus-one-name-label)]
     [:div.secondary-name-row
      [:input#secondary-display-name.secondary-name-input
       {:type "text"
        "data-bind" "secondaryDisplayName"
        :placeholder (t locale :rsvp/plus-one-name-placeholder)
        :maxlength "30"
        "data-on:keydown" (str "evt.key === 'Enter' && @post('"
                               (u "/api/profile/secondary-display-name") "')")}]
      [:button.btn.btn-save
       {:data-indicator "savingSecondaryName"
        "data-attr-disabled" "$savingSecondaryName"
        "data-text" (save-btn-text-expr locale "savingSecondaryName" "savedSecondaryName")
        "data-on:click" (str "@post('"
                             (u "/api/profile/secondary-display-name") "')")}
       (t locale :profile/save)]]]]))

(defn rsvp-fragment
  "Renders the RSVP card. `secondary` is {:user :token-url} when the primary has
   minted a +1 (else nil); ignored entirely for secondary users."
  [locale user rsvp secondary]
  (let [primary? (db/primary? user)
        attending (:attending rsvp)
        status-key (case attending
                     "yes" :rsvp/status-yes
                     "yes_plus_one" :rsvp/status-yes-plus-one
                     "maybe" :rsvp/status-maybe
                     "no" :rsvp/status-no
                     nil)
        status-class (case attending
                       "no" "status-no"
                       "maybe" "status-maybe"
                       "status-yes")]
    (h/html
     [:div#rsvp-form.card
      [:h2 (t locale :rsvp/heading)]
      (when status-key
        [:div.rsvp-status
         [:p {:class status-class} (t locale status-key)]])
      [:div.rsvp-buttons
       (rsvp-button locale attending "yes" "btn-yes" :rsvp/i-coming)
       (when primary?
         (rsvp-button locale attending "yes_plus_one" "btn-yes-plus" :rsvp/we-coming-two))
       (rsvp-button locale attending "maybe" "btn-maybe" :rsvp/maybe)
       (rsvp-button locale attending "no" "btn-no" :rsvp/i-not-coming)]
      (when (and primary? (= attending "yes_plus_one") secondary)
        (secondary-link-panel locale secondary))
      [:div.info-section
       [:label {:for "additional-info"} (t locale :rsvp/info-label)]
       [:textarea#additional-info
        {"data-bind" "additionalInfo"
         "data-class-just-saved" "$savedInfo"
         :placeholder (t locale :rsvp/info-placeholder)
         :maxlength "500"
         :rows "3"}
        (or (:additional-info rsvp) "")]
       [:button.btn.btn-save
        {:data-indicator "savingInfo"
         "data-attr-disabled" "$savingInfo"
         "data-text" (save-btn-text-expr locale "savingInfo" "savedInfo")
         "data-on:click" (str "@post('" (u "/api/rsvp/info") "')")}
        (t locale :rsvp/save)]]])))

(defn- bookmark-hint
  "Dismissible 'bookmark me' nudge shown on the home page. Stores dismissal in
   localStorage so we don't pester returning visitors."
  [locale]
  [:div.bookmark-hint {:id "bookmark-hint"}
   [:span.bookmark-hint-icon "🔖"]
   [:span.bookmark-hint-text (t locale :home/bookmark-hint)]
   [:button.bookmark-hint-close
    {:type "button"
     :aria-label (t locale :home/bookmark-dismiss)
     :onclick (str "try{localStorage.setItem('summerfest:bm','1')}catch(e){}"
                   "this.parentElement.remove()")}
    "×"]
   [:script (h/raw "
(function(){var h=document.getElementById('bookmark-hint');if(!h)return;
try{if(localStorage.getItem('summerfest:bm'))h.remove()}catch(e){}})();")]])

(defn- event-card
  "Top panel on the home page: event photo + address + time."
  [locale]
  [:div.card.event-card
   [:div.event-hero
    ;; [:img.event-photo {:src (u "/home-garden.jpg")
    ;;                    :alt (t locale :event/photo-alt)}]
    [:img.event-photo {:src (u "/home-host.jpg")
                       :alt (t locale :event/host-alt)}]]
   [:dl.event-details
    [:dt (t locale :event/when)] [:dd (t locale :event/time)]
    [:dt (t locale :event/where)] [:dd (t locale :event/address)]]
   [:p.event-invite (t locale :event/invite)]])

(defn welcome-page
  "First-visit prompt: ask the user to confirm or replace the auto-generated
   random animal name. Plain form POST → redirect to /, single field, autofocus."
  [{:keys [user locale] :as ctx}]
  (layout (t locale :welcome/title) ctx
          [:div.card.welcome-card
           [:h1 (t locale :welcome/title)]
           [:p.welcome-lead (t locale :welcome/lead)]
           [:form.welcome-form
            {:action (u "/api/profile/welcome")
             :method "post"}
            [:label.welcome-label {:for "welcome-name"} (t locale :welcome/label)]
            [:input#welcome-name.welcome-input
             {:type "text"
              :name "displayName"
              :maxlength "30"
              :required true
              :autofocus true
              :value (db/effective-name user)}]
            [:button.btn.btn-save.welcome-save {:type "submit"}
             (t locale :welcome/continue)]
            [:p.welcome-hint.small (t locale :welcome/hint)]]]))

(defn home-page [ctx rsvp secondary]
  (let [{:keys [user locale]} ctx
        sec-name (or (:display-name (:user secondary)) "")]
    (layout (t locale :nav/home) ctx
            (bookmark-hint locale)
            (event-card locale)
            [:div {:id "rsvp-section"
                   :data-signals (str "{attending: "
                                      (if-let [a (:attending rsvp)]
                                        (str "'" a "'")
                                        "null")
                                      ", additionalInfo: '"
                                      (js-string-escape (or (:additional-info rsvp) ""))
                                      "', secondaryDisplayName: '"
                                      (js-string-escape sec-name)
                                      "'}")}
             (rsvp-fragment locale user rsvp secondary)])))

;; --- Impressum ---

(defn impressum-page [ctx]
  (let [locale (:locale ctx)]
    (layout (t locale :impressum/title) ctx
            [:div.card
             [:h1 (t locale :impressum/title)]
             [:h3 (t locale :impressum/responsible)]
             [:p (t locale :impressum/name) [:br]
              (interpose [:br] (str/split-lines (t locale :impressum/address)))]
             [:h3 (t locale :impressum/contact)]
             [:p (t locale :contact/email) ": "
              [:a {:href "mailto:Benjamin.Schwerdtner@gmail.com"} "Benjamin.Schwerdtner@gmail.com"]]
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
                 {:type "file" :name "photo" :accept "image/*"
                  :hidden true
                  :onchange "this.form.submit()"}]])]
            (photo-grid locale photos)
            [:div#lightbox.lightbox {:role "dialog" :aria-modal "true" :hidden true}
             [:button.lightbox-close {:type "button" :aria-label (t locale :gallery/close)} "×"]
             [:button.lightbox-prev {:type "button" :aria-label (t locale :gallery/prev)} "‹"]
             [:img#lightbox-img.lightbox-img {:alt ""}]
             [:button.lightbox-next {:type "button" :aria-label (t locale :gallery/next)} "›"]]
            [:script (h/raw "
(function(){
  var box=document.getElementById('lightbox'),img=document.getElementById('lightbox-img');
  if(!box||!img)return;
  var grid=document.getElementById('photo-grid'),idx=-1,srcs=[],fromPop=false;
  function refresh(){srcs=Array.from(grid.querySelectorAll('.photo-card img')).map(function(i){return i.dataset.full||i.currentSrc||i.src})}
  function show(i){if(!srcs.length)return;idx=(i+srcs.length)%srcs.length;img.src=srcs[idx]}
  function open(i){
    refresh();show(i);box.hidden=false;document.body.style.overflow='hidden';
    history.pushState({__lightbox:true},'');
  }
  function close(){
    if(box.hidden)return;
    box.hidden=true;img.removeAttribute('src');document.body.style.overflow='';
    if(!fromPop&&history.state&&history.state.__lightbox){history.back()}
    fromPop=false;
  }
  if(grid){grid.addEventListener('click',function(e){
    var card=e.target.closest('.photo-card');if(!card)return;
    var cards=Array.from(grid.querySelectorAll('.photo-card'));
    var i=cards.indexOf(card);if(i>=0)open(i)
  })}
  box.querySelector('.lightbox-close').addEventListener('click',close);
  box.querySelector('.lightbox-prev').addEventListener('click',function(e){e.stopPropagation();show(idx-1)});
  box.querySelector('.lightbox-next').addEventListener('click',function(e){e.stopPropagation();show(idx+1)});
  box.addEventListener('click',function(e){if(e.target===box)close()});
  document.addEventListener('keydown',function(e){
    if(box.hidden)return;
    if(e.key==='Escape')close();
    else if(e.key==='ArrowLeft')show(idx-1);
    else if(e.key==='ArrowRight')show(idx+1)
  });
  window.addEventListener('popstate',function(){
    if(!box.hidden){fromPop=true;close()}
  });
})();
")])))

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
            [:script (h/raw "
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
")])))

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
