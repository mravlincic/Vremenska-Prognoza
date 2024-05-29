package com.example.prognozarest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Key;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import com.sun.management.OperatingSystemMXBean;

@SpringBootApplication
public class PrognozaRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrognozaRestApplication.class, args);
    }

    @RestController
    public static class WeatherController {
        private static final String API_KLJUC = "c3Sr9%#9#Y#]gQj@y$_*8";
        private static final String TAJNI_KLJUC = "0123456789abcdef";


        //endpoint prognoza
        @GetMapping("/prognoza/{lokacija}")
        public ResponseEntity<?> handlePrognoza(@PathVariable String lokacija,
                                                @RequestHeader("api-kljuc") String apiKljuc,
                                                @RequestHeader("uloga") int uloga_id) {
            try {
                if (API_KLJUC.equals(dekriptiraj(apiKljuc, TAJNI_KLJUC))) {
                    if (uloga_id == 1 || uloga_id == 2) {
                        PodaciOLokaciji podaciOLokaciji = dohvatiPodatkeAPI(lokacija);
                        if (podaciOLokaciji != null) {
                            return ResponseEntity.ok(podaciOLokaciji);
                        } else {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška prilikom dohvaćanja vremenskih podataka za " + lokacija);
                        }
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nedozvoljen pristup");
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nedozvoljen pristup");
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška: " + e.getMessage());
            }
        }

        //admin endpoint
        @GetMapping("/admin")
        public ResponseEntity<?> handleAdmin(@RequestHeader("api-kljuc") String apiKljuc,
                                             @RequestHeader("uloga") int uloga_id) {
            try {
                if (API_KLJUC.equals(dekriptiraj(apiKljuc, TAJNI_KLJUC)) && uloga_id == 1) {
                    JSONObject resursi = CPUiRAMiskoristenje();
                    if (!resursi.isEmpty()) {
                        return ResponseEntity.ok(resursi.toString());
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška prilikom dohvaćanja CPU i RAM iskorištenja.");
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nedozvoljen pristup");
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Greška: " + e.getMessage());
            }
        }

        private double CPUiskoristenje() throws InterruptedException {
            OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

            //inicijalizacija
            osBean.getCpuLoad();
            Thread.sleep(1000);

            return osBean.getCpuLoad() * 100;
        }

        private JSONObject CPUiRAMiskoristenje() {
            JSONObject resursi = new JSONObject();
            try {
                // dohvati iskorištenje CPU-a
                double CPUpostotak = CPUiskoristenje();

                // formatiranje
                String formatiraniCPU = new DecimalFormat("#").format(CPUpostotak) + "%";

                // izracun iskoristenog RAM-a
                double ukupniRAM = Runtime.getRuntime().totalMemory();
                double slobodniRAM = Runtime.getRuntime().freeMemory();
                double RAMpostotak = (ukupniRAM - slobodniRAM) / ukupniRAM * 100;

                // formatiranje
                String formatiraniRAM = new DecimalFormat("#").format(RAMpostotak) + "%";

                // popunjavanje JSON objekta
                resursi.put("cpu", formatiraniCPU);
                resursi.put("ram", formatiraniRAM);
            } catch (Exception e) {
                e.printStackTrace();
                // postavi JSON objekt u null
                resursi = null;
            }
            return resursi;
        }


        // metoda za stvaranje podataka o lokaciji iz JSON odgovora
        private PodaciOLokaciji parsirajPodatke(String jsonOdgovor) {
            // parsiranje JSON odgovora
            JSONObject jsonObjekt = new JSONObject(jsonOdgovor);

            // izvlacenje podataka o lokaciji
            JSONObject lokacijaObjekt = jsonObjekt.getJSONObject("location");
            String imeLokacije = lokacijaObjekt.getString("name");
            double trenutnaTemperatura = jsonObjekt.getJSONObject("current").getDouble("temp_c");
            double trenutnePadaline = jsonObjekt.getJSONObject("current").getDouble("precip_mm");
            double trenutniVjetar = jsonObjekt.getJSONObject("current").getDouble("wind_kph");

            // stvaranje objekta s podacima o lokaciji
            PodaciOLokaciji podaciOLokaciji = new PodaciOLokaciji(imeLokacije, trenutnaTemperatura, trenutnePadaline, trenutniVjetar);

            // izvlacenje podataka o prognozi
            JSONArray poljePrognoza = jsonObjekt.getJSONObject("forecast").getJSONArray("forecastday");
            for (int i = 0; i < poljePrognoza.length(); i++) {
                JSONObject forecastObject = poljePrognoza.getJSONObject(i);
                String danPrognoze = forecastObject.getString("date");
                // ulazni i izlazni format datuma
                SimpleDateFormat pocetniFormat = new SimpleDateFormat("yyyy-MM-dd");
                SimpleDateFormat zavrsniFormat = new SimpleDateFormat("dd.MM.yyyy");

                try {
                    // parsiraj ulazni datum
                    Date date = pocetniFormat.parse(danPrognoze);

                    // formatiraj datum
                    danPrognoze = zavrsniFormat.format(date);

                } catch (ParseException e) {
                    e.printStackTrace();
                }

                JSONObject danObjekt = forecastObject.getJSONObject("day");
                double minTemperatura = danObjekt.getDouble("mintemp_c");
                double maxTemperatura = danObjekt.getDouble("maxtemp_c");
                double padaline = danObjekt.getDouble("totalprecip_mm");
                double vjetar = danObjekt.getDouble("maxwind_kph");

                // dodaj prognozu u podatke o lokaciji
                podaciOLokaciji.dodajPrognozu(new Prognoza(danPrognoze, minTemperatura, maxTemperatura, padaline, vjetar));
            }

            return podaciOLokaciji;
        }

        // metoda za dohvacanje podataka za lokaciju s REST servisa
        private PodaciOLokaciji dohvatiPodatkeAPI(String lokacija) {
            try {
                // konstruiranje URL-a za API poziv
                String apiUrl = "https://api.weatherapi.com/v1/forecast.json?key=2f7abfcd7d2a4c14b6772843241003&q=" + lokacija + "&days=3&aqi=no&alerts=no";
                URL url = new URL(apiUrl);

                // otvori vezu
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // citanje odgovora
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder odgovor = new StringBuilder();
                String linija;
                while ((linija = reader.readLine()) != null) {
                    odgovor.append(linija);
                }
                reader.close();

                // zatvaranje veze
                conn.disconnect();

                // parsiranje odgovora i stvoranje objekta
                return parsirajPodatke(odgovor.toString());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public static String dekriptiraj(String tekst, String kljuc) throws Exception {
            // stvaranje instance za kriptiranje i tajog kljuca
            Cipher cipher = Cipher.getInstance("AES");
            Key tajniKljuc = new SecretKeySpec(kljuc.getBytes("UTF-8"), "AES");

            cipher.init(Cipher.DECRYPT_MODE, tajniKljuc);

            // string u bajtove
            byte[] encryptedBytes = Base64.getDecoder().decode(tekst);

            // dekriptiranje
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // bajtovi u string
            return new String(decryptedBytes, "UTF-8");
        }

        // podaci o lokaciji
        private static class PodaciOLokaciji {
            private String lokacija;
            private double trenutnaTemperatura;
            private double trenutnePadaline;
            private double trenutniVjetar;
            private List<Prognoza> prognoze;

            public PodaciOLokaciji(String lokacija, double trenutnaTemperatura, double trenutnePadaline, double trenutniVjetar) {
                this.lokacija = lokacija;
                this.trenutnaTemperatura = trenutnaTemperatura;
                this.trenutnePadaline = trenutnePadaline;
                this.trenutniVjetar = trenutniVjetar;
                this.prognoze = new ArrayList<>();
            }

            public String getLokacija() {
                return lokacija;
            }

            public double getTrenutnaTemperatura() {
                return trenutnaTemperatura;
            }

            public double getTrenutnePadaline() {
                return trenutnePadaline;
            }

            public double getTrenutniVjetar() {
                return trenutniVjetar;
            }

            public List<Prognoza> getPrognoze() {
                return prognoze;
            }

            public void dodajPrognozu(Prognoza prognoza) {
                prognoze.add(prognoza);
            }
        }

        // podaci o prognozi
        private static class Prognoza {
            private String dan;
            private double minTemperatura;
            private double maxTemperatura;
            private double padaline;
            private double vjetar;

            public Prognoza(String dan, double minTemperatura, double maxTemperatura, double padaline, double vjetar) {
                this.dan = dan;
                this.minTemperatura = minTemperatura;
                this.maxTemperatura = maxTemperatura;
                this.padaline = padaline;
                this.vjetar = vjetar;
            }

            public String getDan() {
                return dan;
            }

            public double getMinTemperatura() {
                return minTemperatura;
            }

            public double getMaxTemperatura() {
                return maxTemperatura;
            }

            public double getPadaline() {
                return padaline;
            }

            public double getVjetar() {
                return vjetar;
            }
        }

    }
}
