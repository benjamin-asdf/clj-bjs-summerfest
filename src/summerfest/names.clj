(ns summerfest.names)

;; All masculine animals so the strong-nominative -er adjective ending fits ("Fauler Fuchs").
;; Adjectives are pre-declined.

(def ^:private adjectives
  ["Fauler" "Fröhlicher" "Mutiger" "Lustiger" "Wilder" "Kluger" "Frecher"
   "Neugieriger" "Schneller" "Leiser" "Glücklicher" "Schlauer" "Sanfter"
   "Tapferer" "Flinker" "Ruhiger" "Freundlicher" "Listiger" "Weiser"
   "Verträumter" "Munterer" "Geschickter"])

(def ^:private animals
  ["Igel" "Fuchs" "Bär" "Hirsch" "Dachs" "Marder" "Wolf" "Specht" "Hase"
   "Otter" "Falke" "Biber" "Maulwurf" "Adler" "Luchs" "Kauz"])

(defn random-german-name []
  (str (rand-nth adjectives) " " (rand-nth animals)))
