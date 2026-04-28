(ns summerfest.i18n)

(def translations
  {:de
   {;; Nav
    :nav/home "Startseite"
    :nav/contact "Kontakt"
    :nav/directions "Anfahrt"
    :nav/gallery "Galerie"
    :nav/chat "Chat"
    :nav/party "Party"
    :nav/hi "Hallo, %s"
    :nav/brand "Bennis Sommerfestle"

    ;; Home
    :home/title "Willkommen bei Bennis Sommerfestle"
    :home/subtitle "Hey %s! Schön, dass du da bist."

    ;; RSVP
    :rsvp/heading "Kommt ihr?"
    :rsvp/we-coming "Wir kommen"
    :rsvp/i-coming "Ich komme"
    :rsvp/we-not-coming "Wir können leider nicht"
    :rsvp/i-not-coming "Ich kann leider nicht"
    :rsvp/status-yes-group "Ihr kommt alle!"
    :rsvp/status-yes-solo "Du kommst!"
    :rsvp/status-no "Wir werden euch vermissen."
    :rsvp/info-label "Gibt es etwas, das wir wissen sollten? (Allergien, Sonderwünsche, etc.)"
    :rsvp/info-placeholder "Sag uns Bescheid..."
    :rsvp/save "Speichern"

    ;; Contact
    :contact/title "Kontakt"
    :contact/intro "Fragen? Hilfe nötig? Meld dich!"
    :contact/email "Email"
    :contact/phone "Telefon"
    :contact/or-chat "Oder nutze einfach den "
    :contact/chat-link "Chat"

    ;; Directions
    :directions/title "Anfahrt"
    :directions/address "Adresse"
    :directions/by-car "Mit dem Auto"
    :directions/by-car-text "Parkplätze vor Ort. Folge den Schildern ab der Autobahnausfahrt."
    :directions/by-transit "Mit öffentlichen Verkehrsmitteln"
    :directions/by-transit-text "Buslinie 42 bis \"Garten-Haltestelle\", dann 5 Min. zu Fuß."
    :directions/map "Karte"
    :directions/map-placeholder "(Karte wird hier eingebettet)"

    ;; Gallery
    :gallery/title "Fotogalerie"
    :gallery/choose "Foto auswählen..."
    :gallery/upload "Hochladen"
    :gallery/empty "Noch keine Fotos. Sei der/die Erste!"
    :gallery/full "Galerie voll – maximale Fotoanzahl erreicht."
    :gallery/uploaded-by "Hochgeladen von %s"
    :gallery/close "Schließen"
    :gallery/prev "Vorheriges Foto"
    :gallery/next "Nächstes Foto"

    ;; Profile
    :profile/edit-name "Anzeigename ändern"
    :profile/edit-name-hint "Klicken um deinen Anzeigename zu ändern"
    :profile/name-placeholder "Dein Anzeigename"
    :profile/save "Speichern"
    :profile/cancel "Abbrechen"

    ;; Chat
    :chat/title "Gäste-Chat"
    :chat/placeholder "Nachricht schreiben..."
    :chat/send "Senden"
    :chat/empty "Noch keine Nachrichten. Starte die Unterhaltung!"
    :chat/pinned "Angepinnt"
    :chat/no-pins "Keine angepinnten Nachrichten"
    :chat/jump-to-latest "Neueste"
    :chat/confirm-unpin "Pin entfernen?"
    :chat/load-older "Ältere laden"

    ;; Login
    :login/title "Bennis Sommerfestle"
    :login/use-link "Bitte nutze den Link, den du erhalten hast."
    :login/lost-link "Link verloren? Kontaktiere den Veranstalter."
    :login/invalid-title "Ups"
    :login/invalid-text "Dieser Link scheint nicht zu funktionieren."
    :login/invalid-hint "Prüfe den Link nochmal oder kontaktiere den Veranstalter."

    ;; Party Games
    :party/title "Party-Spiele"
    :party/subtitle "Verbindet eure Handys und lasst den Ball wandern!"
    :party/bump-title "Bump Connect"
    :party/bump-desc "Stoßt eure Handys zusammen, um sie zu verbinden"
    :party/room-title "Phone Chain"
    :party/room-desc "Ordnet eure Handys an und lasst den Ball wandern"
    :party/radar-title "Compass Radar"
    :party/radar-desc "Eure Handys ordnen sich automatisch nach Kompassrichtung"
    :party/your-name "Dein Name"
    :party/join "Mitmachen"
    :party/players "Spieler"
    :party/bump-hint "Stoßt eure Handys zusammen!"
    :party/room-hint "Tippe auf Namen um die Reihenfolge zu ändern"
    :party/radar-hint "Dreht eure Handys — sie ordnen sich nach Kompass"
    :party/tap-to-launch "Tippen um Ball zu starten"
    :party/back "Zurück"

    ;; Footer
    :footer/text "Bennis Sommerfestle 2026"

    ;; Impressum
    :impressum/title "Impressum"
    :impressum/responsible "Verantwortlich"
    :impressum/name "Benni"
    :impressum/address "[Adresse hier eintragen]"
    :impressum/contact "Kontakt"
    :impressum/disclaimer "Diese Seite ist eine private Einladung zu einer privaten Feier."}

   :en
   {;; Nav
    :nav/home "Home"
    :nav/contact "Contact"
    :nav/directions "Getting Here"
    :nav/gallery "Gallery"
    :nav/chat "Chat"
    :nav/party "Party"
    :nav/hi "Hi, %s"
    :nav/brand "Bennis Sommerfestle"

    ;; Home
    :home/title "Welcome to Bennis Sommerfestle"
    :home/subtitle "Hey %s! So glad you're here."

    ;; RSVP
    :rsvp/heading "Are you coming?"
    :rsvp/we-coming "We're coming"
    :rsvp/i-coming "I'm coming"
    :rsvp/we-not-coming "We can't make it"
    :rsvp/i-not-coming "I can't make it"
    :rsvp/status-yes-group "You're all coming!"
    :rsvp/status-yes-solo "You're coming!"
    :rsvp/status-no "We'll miss you."
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
    :gallery/full "Gallery full — photo limit reached."
    :gallery/uploaded-by "Uploaded by %s"
    :gallery/close "Close"
    :gallery/prev "Previous photo"
    :gallery/next "Next photo"

    ;; Profile
    :profile/edit-name "Edit display name"
    :profile/edit-name-hint "Click to change your display name"
    :profile/name-placeholder "Your display name"
    :profile/save "Save"
    :profile/cancel "Cancel"

    ;; Chat
    :chat/title "Guest Chat"
    :chat/placeholder "Type a message..."
    :chat/send "Send"
    :chat/empty "No messages yet. Start the conversation!"
    :chat/pinned "Pinned"
    :chat/no-pins "No pinned messages"
    :chat/jump-to-latest "Latest"
    :chat/confirm-unpin "Remove pin?"
    :chat/load-older "Load older"

    ;; Login
    :login/title "Bennis Sommerfestle"
    :login/use-link "Please use the link you received to enter."
    :login/lost-link "If you lost your link, contact the organizer."
    :login/invalid-title "Oops"
    :login/invalid-text "This link doesn't seem to work."
    :login/invalid-hint "Double-check the link or contact the organizer."

    ;; Party Games
    :party/title "Party Games"
    :party/subtitle "Connect your phones and pass the ball!"
    :party/bump-title "Bump Connect"
    :party/bump-desc "Bump your phones together to link them"
    :party/room-title "Phone Chain"
    :party/room-desc "Arrange your phones and pass the ball"
    :party/radar-title "Compass Radar"
    :party/radar-desc "Phones auto-arrange by compass heading"
    :party/your-name "Your name"
    :party/join "Join"
    :party/players "Players"
    :party/bump-hint "Bump phones together!"
    :party/room-hint "Tap names to reorder the chain"
    :party/radar-hint "Rotate your phones — they arrange by compass"
    :party/tap-to-launch "Tap to launch ball"
    :party/back "Back"

    ;; Footer
    :footer/text "Bennis Sommerfestle 2026"

    ;; Impressum
    :impressum/title "Imprint"
    :impressum/responsible "Responsible"
    :impressum/name "Benni"
    :impressum/address "[Address goes here]"
    :impressum/contact "Contact"
    :impressum/disclaimer "This site is a private invitation to a private celebration."}})

(def default-locale :de)

(defn t
  "Translate a key for the given locale. Falls back to :en, then to the key name.
   Supports format args: (t :de :nav/hi \"Max\") => \"Hallo, Max\""
  [locale key & args]
  (let [s (or (get-in translations [locale key])
              (get-in translations [:en key])
              (name key))]
    (if (seq args)
      (apply format s args)
      s)))
