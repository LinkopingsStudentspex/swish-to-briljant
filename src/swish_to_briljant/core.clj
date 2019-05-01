(ns swish-to-briljant.core
  (:require [dk.ative.docjure.spreadsheet :as dc]
            [bg-to-briljant.arguments     :refer [validate-args]]
            [swish-to-briljant.utilities  :refer [condp-map re-find-safe capitalize-words]])
  (:gen-class))

(def settings (read-string (slurp "settings.edn")))
(def headers    "PREL\n1;.U\n")
(def dokument (dc/load-workbook "in/Swishrapport.xls"))

(defn kategorisera
  "Kategorisera en transaktion att vara i någon av de kotegorier som
  anges i inställningarna för programmet."
  [transaktion]
  (assoc transaktion :kategori
         ;; Antingen lyckas en utav de två olika
         ;; kategoriseringsmetoderna klassa transaktionen eller så får
         ;; den bli kallad för ~okategoriserad~ fika.
         (or (condp-fn re-find-safe (:meddelande transaktion) (:meddelande-regex->kategori settings))
             (condp-fn ==           (:belopp     transaktion) (:belopp->kategori           settings))
             :fika)))

(defn associera-kreditkonto
  "Var transaktion bör debitera ett kreditkonto. Denna funktion
  associerar en transaktion med ett sådant."
  [transaktion]
  (assoc transaktion :kreditkonto ((:kategori transaktion) (:kategori->kreditkonto settings))))

(defn associera-underprojekt
  "Var kategori av transaktioner hör oftast hemma i ett visst
  underprojekt i bokföringen. Dessa är t.ex. uppsättningen eller KM."
  [transaktion]
  (assoc transaktion :underprojekt ((:kategori transaktion) (:kategori->underprojekt settings))))

(defn dokument->transaktioner
  "Extraherar alla transaktioner ur ett swishrapport i
  excelformat. Härleder även en mängd information för var
  transaktion baserat på transaktionens meddelande."
  [dokument]
  (->> dokument
     (dc/select-sheet "Swish-rapport")
     (dc/select-columns {:C :kontonummer   ; Notera att dessa är text, inte tal.
                         :D :bokföringsdag
                         :J :användarnamn
                         :K :meddelande
                         :L :klockslag
                         :M :belopp})
     (drop 2) ; Transaktionerna börjar efter rad 2.
     (map kategorisera)
     (map associera-kreditkonto)
     (map associera-underprojekt)))

(defn transaktion->kredit-csv-string
  "Tar en transaktion och returnerar kredit-delen av transaktionen
  som en CSV-string."
  [{:keys [kreditkonto kreditunderkonto underprojekt belopp meddelande användarnamn datum]}]
  (str ";" datum
       ";" kreditkonto
       ";" kreditunderkonto
       ";;;" (if underprojekt (str (:projekt settings)","underprojekt) "")
       ";;" (- belopp)
       ";" meddelande " " (capitalize-words användarnamn)
       ";;"))

(defn transaktion->debet-csv-string
  "Tar en transaktion och returnerar debet-delen av transaktionen som
  en CSV-string."
  [{:keys [debetkonto underprojekt belopp meddelande användarnamn datum]}]
  (str ";" datum
       ";" debetkonto
       ";;;;" (if underprojekt (str (:projekt settings)","underprojekt) "")
       ";;" (- belopp)
       ";" meddelande " " (capitalize-words användarnamn)
       ";;"))

(defn dokument->datumintervall
  [dokument]
  (let [meddelande (->> dokument
                        (dc/select-sheet "Swish-rapport")
                        (dc/select-cell "A1")
                        dc/read-cell)
        meddelandevektor (clojure.string/split meddelande #" ")]
    (str (get meddelandevektor 3) "_-_" (get meddelandevektor 5))))



(defn -main
  [& args]
  (let [{:keys [arguments options exit-message ok?]} (validate-args args)]
    (if exit-message
      (do (println exit-message)
          (System/exit (if ok? 0 1)))
      (for [dokument (map dc/load-workbook arguments)]
        (let [outpath (str "out/" (dokument->datumintervall dokument) ".csv")]
          (println "Writing to " outpath)
          (spit outpath
                (str headers
                     (->> dokument
                          dokument->transaktioner
                          (map transaktion->csv-string)
                          (clojure.string/join "\n"))
                     "\n")))))))

(-main "in/Swishrapport.xls")
