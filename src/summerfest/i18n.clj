(ns summerfest.i18n)

(def translations
  {:de
   { ;; Nav
    :nav/home "Startseite"
    :nav/contact "Kontakt"
    :nav/gallery "Galerie"
    :nav/chat "Chat"
    :nav/party "Party"
    :nav/hi "Hallo, %s"
    :nav/brand "Bennis Sommerfestle"

    ;; Home
    :home/title "Willkommen bei Bennis Sommerfestle"
    :home/subtitle "Hey %s! Schön, dass du da bist."
    :home/bookmark-hint "Tipp: Speicher diese Seite als Lesezeichen, damit du den Link nicht verlierst."
    :home/bookmark-dismiss "Hinweis schließen"

    ;; Welcome (first visit)
    :welcome/title "Willkommen!"
    :welcome/lead "Schön, dass du da bist. Wie sollen wir dich nennen?"
    :welcome/label "Dein Anzeigename"
    :welcome/continue "Los geht's"
    :welcome/hint "Du kannst deinen Namen später jederzeit oben in der Navigation ändern."

    ;; Event info panel
    :event/when "Wann"
    :event/where "Wo"
    :event/time "08.08.2026; ab 13:00 Uhr. Essen ab 14:30 Uhr. Open End."
    :event/address "Auf dem Emmerberge 8
30169 Hannover"
    :event/photo-alt "Sommerfest"
    :event/host-alt "Dein Gastgeber"
    :event/invite "Ihr seid herzlich zu meinem Sommerfestle eingeladen. Ein kleines Sit-in — auf gut Schwäbisch: eine Hocketse — unterm Zeltdach, an Bierbänken im Hannoveraner Innenhof, mit veganem Essen, kalter Mate und viel Zeit. Mitbringen müsst ihr nichts außer euch selbst; Kuchen oder ähnlicher Nachtisch ist immer willkommen. Falls ihr Sonntag noch in Hannover seid: wir machen was zusammen. Sagt einfach Bescheid."

    ;; RSVP
    :rsvp/heading "Kommst du?"
    :rsvp/i-coming "Ja, ich komme"
    :rsvp/we-coming-two "Wir kommen zu zweit"
    :rsvp/maybe "Vielleicht"
    :rsvp/i-not-coming "Ich kann leider nicht"
    :rsvp/status-yes "Du kommst!"
    :rsvp/status-yes-plus-one "Ihr kommt zu zweit!"
    :rsvp/status-maybe "Du überlegst noch."
    :rsvp/status-no "Schade"
    :rsvp/info-label "Möchtest du uns etwas mitteilen?"
    :rsvp/info-placeholder "Allergien"
    :rsvp/save "Speichern"
    :rsvp/plus-one-intro "Hier ist der Link für deine Begleitung:"
    :rsvp/plus-one-note "Schick ihn an deine Begleitung. Sie kann dann selbst zusagen, im Chat schreiben und Fotos hochladen."
    :rsvp/plus-one-share-text "Hier ist dein Link fürs Sommerfest:"
    :rsvp/plus-one-name-label "Name deiner Begleitung"
    :rsvp/plus-one-name-placeholder "Name deiner Begleitung"

    ;; Share
    :share/copy "Kopieren"
    :share/copied "Kopiert!"
    :share/share "Teilen"
    :share/title "Bennis Sommerfestle"

    ;; Contact
    :contact/title "Kontakt"
    :contact/intro "Fragen? Hilfe nötig? Meld dich!"
    :contact/email "Email"
    :contact/phone "Telefon"
    :contact/or-chat "Oder nutze einfach den "
    :contact/chat-link "Chat"

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

    ;; Generic UI feedback
    :ui/saving "Speichert…"
    :ui/saved "✓ Gespeichert"

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
    :login/lost-link "Link verloren? Kontaktiere Benni."
    :login/invalid-title "Ups"
    :login/invalid-text "Dieser Link scheint nicht zu funktionieren."
    :login/invalid-hint "Prüfe den Link nochmal oder kontaktiere Benni."

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
    :impressum/address "Benjamin Schwerdtner
Auf dem Emmerberge 8
30169 Hannover
"
    :impressum/contact "Kontakt"
    :impressum/disclaimer "Diese Seite ist eine private Einladung zu einer privaten Feier."}

   :en
   {;; Nav
    :nav/home "Home"
    :nav/contact "Contact"
    :nav/gallery "Gallery"
    :nav/chat "Chat"
    :nav/party "Party"
    :nav/hi "Hi, %s"
    :nav/brand "Bennis Sommerfestle"

    ;; Home
    :home/title "Welcome to Bennis Sommerfestle"
    :home/subtitle "Hey %s! So glad you're here."
    :home/bookmark-hint "Tip: bookmark this page so you don't lose your link."
    :home/bookmark-dismiss "Dismiss hint"

    ;; Welcome (first visit)
    :welcome/title "Welcome!"
    :welcome/lead "So glad you're here. What should we call you?"
    :welcome/label "Your display name"
    :welcome/continue "Let's go"
    :welcome/hint "You can change your name any time from the top navigation."

    ;; Event info panel
    :event/when "When"
    :event/where "Where"
    :event/time "08.08.2026; Start: 1pm Food: 2:30pm. Open End."
    :event/address "Auf dem Emmerberge 8
30169 Hannover"
    :event/photo-alt "Summer Fest"
    :event/host-alt "Your host"
    :event/invite "You're warmly invited to my Sommerfestle. A little sit-in — or, in Swabian: a Hocketse — under a tent, on beer benches in a Hannover courtyard garden, with vegan food, cold Mate and plenty of time. Bring nothing but yourselves; cake or any kind of dessert is always welcome. If you're still in Hannover on Sunday: let's do something together. Just let me know."

    ;; RSVP
    :rsvp/heading "Are you coming?"
    :rsvp/i-coming "Yes, I'm coming"
    :rsvp/we-coming-two "We're coming as a pair"
    :rsvp/maybe "Maybe"
    :rsvp/i-not-coming "I can't make it"
    :rsvp/status-yes "You're coming!"
    :rsvp/status-yes-plus-one "You're coming as a pair!"
    :rsvp/status-maybe "You're still thinking it over."
    :rsvp/status-no "We'll miss you."
    :rsvp/info-label "Anything you want to say?"
    :rsvp/info-placeholder "Allergies"
    :rsvp/save "Save"
    :rsvp/plus-one-intro "Here's the link for your +1:"
    :rsvp/plus-one-note "Send it to your +1. They'll be able to RSVP themselves, chat and upload photos."
    :rsvp/plus-one-share-text "Here's your link for the summer fest:"
    :rsvp/plus-one-name-label "Your +1's name"
    :rsvp/plus-one-name-placeholder "Your +1's name"

    ;; Share
    :share/copy "Copy"
    :share/copied "Copied!"
    :share/share "Share"
    :share/title "Bennis Sommerfestle"

    ;; Contact
    :contact/title "Contact"
    :contact/intro "Questions? Need help? Reach out!"
    :contact/email "Email"
    :contact/phone "Phone"
    :contact/or-chat "Or just use the "
    :contact/chat-link "chat"

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

    ;; Generic UI feedback
    :ui/saving "Saving…"
    :ui/saved "✓ Saved"

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
    :login/lost-link "If you lost your link, contact Benni."
    :login/invalid-title "Oops"
    :login/invalid-text "This link doesn't seem to work."
    :login/invalid-hint "Double-check the link or contact Benni."

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
    :impressum/address "Benjamin Schwerdtner
Auf dem Emmerberge 8
30169 Hannover
"
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
