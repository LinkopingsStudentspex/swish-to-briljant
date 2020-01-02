(ns swish-to-briljant.core
  (:require [dk.ative.docjure.spreadsheet :as dc]
            [swish-to-briljant.arguments     :refer [validate-args]]
            [swish-to-briljant.utilities  :refer [condp-fn re-find-safe capitalize-words tprn]])
  (:gen-class))

(def settings (read-string (slurp "settings.edn")))
(def headers    "PREL\n1;.U\n")

(defn kategorisera
  "Kategorisera en transaktion att vara i någon av de kotegorier som
  anges i inställningarna för programmet."
  [transaktion]
  (assoc transaktion :kategori
         ;; Antingen lyckas en utav de tre olika kategoriseringsmetoderna
         ;; klassa transaktionen eller så får den bli kallad okategoriserad.
         (or (condp-fn re-find-safe        (:meddelande transaktion) (:meddelande-regex->kategori settings))
             (condp-fn ==                  (:belopp     transaktion) (:absolutbelopp->kategori    settings))
             (condp-fn #(== (mod %2 %1) 0) (:belopp     transaktion) (:delbelopp->kategori        settings))
             :okategoriserat)))

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
                         :E :transaktionsdag
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
       (map associera-underprojekt)
       (map #(assoc % :transaktionsdag (:transaktionsdag (first transaktioner))))))

(defn transaktion->csv-string
  "Tar en typ av transaktion en transaktion och returnerar den på
  Briljants CSV-format. Det första argumentet avgör om debet eller
  kredit-delen av transaktionen skrivs ut, giltiga värden
  är :debet eller :kredit."
  [typ {:keys [kreditkonto debetkonto kreditunderkonto underprojekt belopp meddelande användarnamn transaktionsdag]}]
  (str ";" transaktionsdag
       ";" (if   (= typ :kredit) kreditkonto debetkonto)
       ";" (when (= typ :kredit) kreditunderkonto)
       ";;;" (if underprojekt (str (:projekt settings)","underprojekt) "")
       ";;" (if  (= typ :kredit) (- belopp) belopp)
       ";" (if   (= typ :kredit)
             (str "Swish-bet. " transaktionsdag)
             (str meddelande " " (capitalize-words användarnamn)))
       ";;"))

(defn dokument->datumintervall
  "Tar en swishrapport och returnerar det datumintervall som
  rapporten gäller för."
  [dokument]
  (let [meddelande (->> dokument
                        (dc/select-sheet "Swish-rapport")
                        (dc/select-cell "A1")
                        dc/read-cell)
        meddelandevektor (clojure.string/split meddelande #" ")]
    (str (get meddelandevektor 3) "_-_" (get meddelandevektor 5))))

(defn load-workbook-or-terminate
  "Laddar en fil om den existerar och går eller terminerar programmet
  efter att ha gett användaren felmeddelandet"
  [file-path]
  (try
    (dc/load-workbook file-path)
    (catch Exception e
      (println (str "Kunde inte ladda excelarket " file-path " av följande anledning: " (.getMessage e)))
      (System/exit 2))))

(defn -main
  "Programmets huvudfunktion."
  [& args]
  (let [{:keys [arguments options exit-message ok?]} (validate-args args)]
    (if exit-message
      (do (println exit-message)
          (System/exit (if ok? 0 1)))
      (for [dokument (map load-workbook-or-terminate arguments)]
        (let [outpath       (str "out/" (dokument->datumintervall dokument) ".csv")
              transaktioner (dokument->transaktioner dokument)]
          (println "Skriver CSV-fil för briljant till " outpath)
          (spit outpath
                (str headers
                     (->> transaktioner
                          (sort-by :transaktionsdag)
                          (partition-by :transaktionsdag)
                          (map (juxt #(map (partial transaktion->csv-string :debet) %)
                                     #(->> %
                                           gruppera-krediteringar
                                           (map (partial transaktion->csv-string :kredit)))))
                          (map flatten)
                          flatten
                          (clojure.string/join "\n"))
                     "\n")))))))
