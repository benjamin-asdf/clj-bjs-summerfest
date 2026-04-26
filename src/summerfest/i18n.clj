(ns summerfest.i18n)

(def translations
  {:de
   {;; Nav
    :nav/home "Startseite"
    :nav/contact "Kontakt"
    :nav/directions "Anfahrt"
    :nav/gallery "Galerie"
    :nav/chat "Chat"
    :nav/hi "Hallo, %s!"
    :nav/brand "🌻 Sommerfest"

    ;; Home
    :home/title "Willkommen zum Sommerfest 2026!"
    :home/subtitle "Hey %s! Schoen, dass du da bist."

    ;; RSVP
    :rsvp/heading "Kommt ihr?"
    :rsvp/we-coming "Wir kommen!"
    :rsvp/i-coming "Ich komme!"
    :rsvp/we-not-coming "Wir koennen leider nicht"
    :rsvp/i-not-coming "Ich kann leider nicht"
    :rsvp/status-yes-group "Ihr kommt alle! Woohoo!"
    :rsvp/status-yes-solo "Du kommst! Woohoo!"
    :rsvp/status-no "Wir werden euch vermissen!"
    :rsvp/info-label "Gibt es etwas, das wir wissen sollten? (Allergien, Sonderwuensche, etc.)"
    :rsvp/info-placeholder "Sag uns Bescheid..."
    :rsvp/save "Speichern"

    ;; Contact
    :contact/title "Kontakt"
    :contact/intro "Fragen? Hilfe noetig? Meld dich!"
    :contact/email "Email"
    :contact/phone "Telefon"
    :contact/or-chat "Oder nutze einfach den "
    :contact/chat-link "Chat"

    ;; Directions
    :directions/title "Anfahrt"
    :directions/address "Adresse"
    :directions/by-car "Mit dem Auto"
    :directions/by-car-text "Parkplaetze vor Ort. Folge den Schildern ab der Autobahnausfahrt."
    :directions/by-transit "Mit oeffentlichen Verkehrsmitteln"
    :directions/by-transit-text "Buslinie 42 bis \"Garten-Haltestelle\", dann 5 Min. zu Fuss."
    :directions/map "Karte"
    :directions/map-placeholder "(Karte wird hier eingebettet)"

    ;; Gallery
    :gallery/title "Fotogalerie"
    :gallery/choose "Foto auswaehlen..."
    :gallery/upload "Hochladen"
    :gallery/empty "Noch keine Fotos. Sei der/die Erste!"

    ;; Chat
    :chat/title "Gaeste-Chat"
    :chat/placeholder "Nachricht schreiben..."
    :chat/send "Senden"
    :chat/empty "Noch keine Nachrichten. Starte die Unterhaltung!"

    ;; Login
    :login/title "🌻 Sommerfest 2026"
    :login/use-link "Bitte nutze den Link, den du erhalten hast."
    :login/lost-link "Link verloren? Kontaktiere den Veranstalter."
    :login/invalid-title "🌻 Ups!"
    :login/invalid-text "Dieser Link scheint nicht zu funktionieren."
    :login/invalid-hint "Pruefe den Link nochmal oder kontaktiere den Veranstalter."

    ;; Footer
    :footer/text "🌻 Sommerfest 2026"}

   :en
   {;; Nav
    :nav/home "Home"
    :nav/contact "Contact"
    :nav/directions "Getting Here"
    :nav/gallery "Gallery"
    :nav/chat "Chat"
    :nav/hi "Hi, %s!"
    :nav/brand "🌻 Summer Fest"

    ;; Home
    :home/title "Welcome to Summer Fest 2026!"
    :home/subtitle "Hey %s! So glad you're here."

    ;; RSVP
    :rsvp/heading "Are you coming?"
    :rsvp/we-coming "We're coming!"
    :rsvp/i-coming "I'm coming!"
    :rsvp/we-not-coming "We can't make it"
    :rsvp/i-not-coming "I can't make it"
    :rsvp/status-yes-group "You're all coming! Woohoo!"
    :rsvp/status-yes-solo "You're coming! Woohoo!"
    :rsvp/status-no "We'll miss you!"
    :rsvp/info-label "Anything we should know? (allergies, special needs, etc.)"
    :rsvp/info-placeholder "Tell us anything..."
    :rsvp/save "Save"

    ;; Contact
    :contact/title "Contact"
    :contact/intro "Questions? Need help? Reach out!"
    :contact/email "Email"
    :contact/phone "Phone"
    :contact/or-chat "Or just use the "
    :contact/chat-link "chat"

    ;; Directions
    :directions/title "How to Get Here"
    :directions/address "Address"
    :directions/by-car "By Car"
    :directions/by-car-text "Parking is available on-site. Follow signs from the highway exit."
    :directions/by-transit "By Public Transport"
    :directions/by-transit-text "Take bus line 42 to \"Garden Stop\", then 5 min walk."
    :directions/map "Map"
    :directions/map-placeholder "(Map will be embedded here)"

    ;; Gallery
    :gallery/title "Photo Gallery"
    :gallery/choose "Choose a photo..."
    :gallery/upload "Upload"
    :gallery/empty "No photos yet. Be the first to share!"

    ;; Chat
    :chat/title "Guest Chat"
    :chat/placeholder "Type a message..."
    :chat/send "Send"
    :chat/empty "No messages yet. Start the conversation!"

    ;; Login
    :login/title "🌻 Summer Fest 2026"
    :login/use-link "Please use the link you received to enter."
    :login/lost-link "If you lost your link, contact the organizer."
    :login/invalid-title "🌻 Oops!"
    :login/invalid-text "This link doesn't seem to work."
    :login/invalid-hint "Double-check the link or contact the organizer."

    ;; Footer
    :footer/text "🌻 Summer Fest 2026"}})

(def default-locale :de)

(defn t
  "Translate a key for the given locale. Falls back to :en, then to the key name.
   Supports format args: (t :de :nav/hi \"Max\") => \"Hallo, Max!\""
  [locale key & args]
  (let [s (or (get-in translations [locale key])
              (get-in translations [:en key])
              (name key))]
    (if (seq args)
      (apply format s args)
      s)))
