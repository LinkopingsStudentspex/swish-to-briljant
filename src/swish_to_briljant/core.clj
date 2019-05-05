(ns swish-to-briljant.core
  (:require [dk.ative.docjure.spreadsheet :as dc]
            [bg-to-briljant.arguments     :refer [validate-args]]
            [swish-to-briljant.utilities  :refer [condp-fn re-find-safe capitalize-words tprn]])
  (:gen-class))

(def settings (read-string (slurp "settings.edn")))
(def headers    "PREL\n1;.U\n")
(def dokument (dc/load-workbook "in/Swishrapport.xls"))

(defn kategorisera
  "Kategorisera en transaktion att vara i någon av de kotegorier som
  anges i inställningarna för programmet."
  [transaktion]
  (assoc transaktion :kategori
         ;; Antingen lyckas en utav de två olika kategoriseringsmetoderna
         ;; klassa transaktionen eller så får den bli kallad för fika.
         (or (condp-fn re-find-safe (:meddelande transaktion) (:meddelande-regex->kategori settings))
             (condp-fn ==           (:belopp     transaktion) (:belopp->kategori           settings))
             :fika)))

(defn associera-kreditkonto
  "Var transaktion bör belasta ett kreditkonto. Denna funktion
  associerar en transaktion med ett sådant."
  [transaktion]
  (assoc transaktion :kreditkonto  (get (:kategori->kreditkonto settings)   (:kategori transaktion))))

(defn associera-debetkonto
  "I den swish-rapport som vi får skickade till oss har var
  transaktion ett debetkontot på banken associerat med sig. Vi
  översätter här bankkontonummeret till debetkonto i bokföring."
  [transaktion]
  (assoc transaktion :debetkonto   (get (:kontonummer->debetkonto settings) (:kontonummer transaktion))))

(defn associera-underprojekt
  "Var kategori av transaktioner hör oftast hemma i ett visst
  underprojekt i bokföringen. Dessa är t.ex. uppsättningen eller KM."
  [transaktion]
  (assoc transaktion :underprojekt (get (:kategori->underprojekt settings)  (:kategori transaktion) )))

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
     (map associera-debetkonto)
     (map associera-underprojekt)))

(defn gruppera-krediteringar
  "Istället för att låta samtliga små betalningar belasta
  kreditkontona grupperar vi ihop dom till ett fåtal stora"
  [transaktioner]
  (->> transaktioner
       (group-by :kategori)
       (reduce-kv (fn [result key value]
                    (assoc result key (reduce #(+ (:belopp %2) %1) 0 value)))
                  {})
       (map (fn [nyckel-värde-par] {:kategori (first  nyckel-värde-par)
                                    :belopp   (second nyckel-värde-par)
                                    :projekt  (:projekt settings)}))
       (map associera-kreditkonto)
       (map associera-underprojekt)))

(defn transaktion->csv-string
  "Tar en typ av transaktion en transaktion och returnerar den på
  Briljants CSV-format. Det första argumentet avgör om debet eller
  kredit-delen av transaktionen skrivs ut, giltiga värden
  är :debet eller :kredit."
  [typ {:keys [kreditkonto debetkonto kreditunderkonto underprojekt belopp meddelande användarnamn bokföringsdag]}]
  (str ";" bokföringsdag
       ";" (if   (= typ :kredit) kreditkonto debetkonto)
       ";" (when (= typ :kredit) kreditunderkonto)
       ";;;" (if underprojekt (str (:projekt settings)","underprojekt) "")
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
        (let [outpath       (str "out/" (dokument->datumintervall dokument) ".csv")
              transaktioner (dokument->transaktioner dokument)]
          (println "Writing to " outpath)
          (spit outpath
                (str headers
                     (->> transaktioner
                          (map (partial transaktion->csv-string :debet))
                          (clojure.string/join "\n"))
                     "\n"
                     (->> transaktioner
                          (gruppera-krediteringar)
                          (map (partial transaktion->csv-string :kredit))
                          (clojure.string/join "\n"))
                     "\n")))))))
