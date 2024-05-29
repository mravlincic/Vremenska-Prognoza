import com.formdev.flatlaf.FlatLightLaf;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.Key;
import java.sql.*;
import java.util.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


public class Glavni extends JFrame {

    private JPanel glavniForm;
    private JButton dodajBtn;
    private JTable tablica;
    private JButton ukloniBtn;
    private JComboBox<String> locationComboBox;
    private JLabel korisnikLbl;
    private JLabel slikaLbl;
    private JButton generirajIzvjesceBtn;

    private Connection veza;


    String izvjesceUspjehE;
    String izvjesceNeuspjehE;
    String dodajBtnNatpisE;
    String dodajBtnNaslovE;
    String unosLokacijeE;
    String ukloniBtnNaslovE;
    String brisanjeLokacijeE;
    String lokacijaUkloniE;
    String zaglavljeTablice;
    String[] poljeZaglavlje;


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

    public Glavni(String korisnickoime, int korisnik_id, int ulogaID, byte[] slika, Locale defLocale, Connection veza) throws SQLException {
        FlatLightLaf.setup();
        setContentPane(glavniForm);
        setTitle("Vremenska Prognoza");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        locationComboBox = new JComboBox<>();
        this.veza = veza;

        ResourceBundle defBundle = ResourceBundle.getBundle("bundle", defLocale);

        dodajBtn.setText(defBundle.getString("dodajBtn"));
        ukloniBtn.setText(defBundle.getString("ukloniBtn"));
        generirajIzvjesceBtn.setText(defBundle.getString("generirajIzvjesceBtn"));
        izvjesceUspjehE = defBundle.getString("izvjesceUspjehE");
        izvjesceNeuspjehE = defBundle.getString("izvjesceNeuspjehE");
        dodajBtnNatpisE = defBundle.getString("dodajBtnNatpisE");
        dodajBtnNaslovE = defBundle.getString("dodajBtnNaslovE");
        unosLokacijeE = defBundle.getString("unosLokacijeE");
        ukloniBtnNaslovE = defBundle.getString("ukloniBtnNaslovE");
        brisanjeLokacijeE = defBundle.getString("brisanjeLokacijeE");
        lokacijaUkloniE = defBundle.getString("lokacijaUkloniE");
        zaglavljeTablice = defBundle.getString("zaglavljeTablice");
        poljeZaglavlje = zaglavljeTablice.split(",");


        //postavljanje imena i slike korisnika
        korisnikLbl.setText(korisnickoime);

        //demonstracija neautoriziranog pristupa admin endpointu
        System.out.println("Odgovor posluzitelja nakon pristupa /admin endpointu s ulogom obicnog korisnika:");
        Admin.dohvatiStatusAPI(ulogaID);

        //spremanje postavki prozora na zatvaranju
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                Prijava.spremiPostavkeProzora(getBounds());
            }
        });

        try {
            // provjera sadrzi li poje za sliku blob
            if (slika != null && slika.length > 0) {
                // citanje slike iz niza bajtova
                BufferedImage originalImg = ImageIO.read(new ByteArrayInputStream(slika));
                // ako je slika ucitana
                if (originalImg != null) {
                    // promjena velicine
                    BufferedImage gotovaSlika = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = gotovaSlika.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); //bicubic resampling kako ne bi slika bila mutna
                    g2d.drawImage(originalImg, 0, 0, 50, 50, null);
                    g2d.dispose();

                    // stvaranje ikone
                    ImageIcon icon = new ImageIcon(gotovaSlika);
                    // postavljanje ikone na labelu
                    slikaLbl.setIcon(icon);
                } else {
                    System.err.println("Greška: Nije moguće učitati sliku. Neispravan format slike ili oštećeni podaci.");
                }
            } else {
                System.err.println("Greška: Primljen je prazan ili null bajtni niz za podatke o slici.");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // inicijalno popunjavanje tablice s lokacijama iz baze
        popuniTablicuIzBaze(korisnik_id, ulogaID);

        // listener za dodavanje lokacije
        dodajBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //unos, dodavanje lokacije u bazu i ucitavanje u tablicu
                String imeLokacije = JOptionPane.showInputDialog(
                        Glavni.this,
                        dodajBtnNatpisE,
                        dodajBtnNaslovE,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (!imeLokacije.isEmpty()) {
                    try {
                        dodajLokacijuUBazu(imeLokacije, korisnik_id);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                    //azuriraj jtable
                    try {
                        popuniTablicuIzBaze(korisnik_id, ulogaID);
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });


        // listener za uklanjanje lokacije
        ukloniBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // stvaranje novog dijaloga
                JDialog removeDialog = new JDialog(Glavni.this, ukloniBtnNaslovE, true);
                removeDialog.setLayout(new BorderLayout());

                // panela za držanje komponenti
                JPanel removePanel = new JPanel(new FlowLayout());
                // popunjavanje combobox lokacija
                try {
                    popuniComboBoxLokacija(korisnik_id);
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                // dodavanje  na panel
                removePanel.add(locationComboBox);

                // gumb za uklanjanje
                JButton removeButton = new JButton("Ukloni");
                removeButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // dohvacanje odabrane lokacije
                        String odabranaLokacija = (String) locationComboBox.getSelectedItem();
                        if (odabranaLokacija != null) {
                            // ukloni odabranu lokaciju iz baze podataka
                            try {
                                ukloniLokacijuIzBaze(odabranaLokacija, korisnik_id);
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }

                            //azuriraj jtable
                            try {
                                popuniTablicuIzBaze(korisnik_id, ulogaID);
                            } catch (SQLException ex) {
                                throw new RuntimeException(ex);
                            }

                            // zatvaranje dijaloga
                            removeDialog.dispose();
                        } else {
                            JOptionPane.showMessageDialog(Glavni.this, lokacijaUkloniE, "Greška",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });

                // dodavanje gumba za uklanjanje na panel
                removePanel.add(removeButton);

                // dodavanje panela na dijalog
                removeDialog.add(removePanel, BorderLayout.CENTER);

                // postavljanje velicine dijaloga i vidljivost
                removeDialog.pack();
                removeDialog.setLocationRelativeTo(Glavni.this);
                removeDialog.setVisible(true);
            }
        });

        generirajIzvjesceBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generirajIzvjesce(korisnickoime);
            }
        });

    }

    // metoda za stvaranje podataka o lokaciji iz JSON odgovora
    private PodaciOLokaciji parsirajPodatke(String jsonOdgovor) {
        // parsiranje JSON odgovora
        JSONObject jsonObject = new JSONObject(jsonOdgovor);

        // izvlacenje podataka o lokaciji
        String imeLokacije = jsonObject.getString("lokacija");
        double trenutnaTemperatura = jsonObject.getDouble("trenutnaTemperatura");
        double trenutnePadaline = jsonObject.getDouble("trenutnePadaline");
        double trenutniVjetar = jsonObject.getDouble("trenutniVjetar");

        // stvaranje objekta s podacima o lokaciji
        PodaciOLokaciji podaciOLokaciji = new PodaciOLokaciji(imeLokacije, trenutnaTemperatura, trenutnePadaline, trenutniVjetar);

        // izvlacenje podataka o prognozi
        JSONArray forecastArray = jsonObject.getJSONArray("prognoze");
        for (int i = 0; i < forecastArray.length(); i++) {
            JSONObject forecastObject = forecastArray.getJSONObject(i);
            String danPrognoze = forecastObject.getString("dan"); // izvlacenje datuma iz objekta prognoze
            double minTemperatura = forecastObject.getDouble("minTemperatura");
            double maxTemperatura = forecastObject.getDouble("maxTemperatura");
            double padaline = forecastObject.getDouble("padaline");
            double vjetar = forecastObject.getDouble("vjetar");

            // dodaj prognozu u podatke o lokaciji
            podaciOLokaciji.dodajPrognozu(new Prognoza(danPrognoze, minTemperatura, maxTemperatura, padaline, vjetar));
        }

        return podaciOLokaciji;
    }


    private PodaciOLokaciji dohvatiPodatkeAPI(String lokacija, int ulogaID) {
        try {
            // konstruiranje urla
            String apiUrl = "http://localhost:8080/prognoza/" + lokacija;
            URL url = new URL(apiUrl);

            // otvranje veze
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // postavljanje zaglavlja
            conn.setRequestProperty("api-kljuc", enkriptiraj("c3Sr9%#9#Y#]gQj@y$_*8", "0123456789abcdef"));
            conn.setRequestProperty("uloga", String.valueOf(ulogaID));


            // provjera odgovora
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
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

                // parsiranje i stvaranje objekta
                PodaciOLokaciji podaciOLokaciji = parsirajPodatke(odgovor.toString());
                return podaciOLokaciji;
            } else {
                System.out.println("HTTP error: " + responseCode);
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // metoda za popunjavanje kombiniranog okvira lokacija
    private void popuniComboBoxLokacija(int korisnik_id) throws SQLException {
        locationComboBox.removeAllItems();
        String upit = "SELECT naziv FROM Lokacije WHERE korisnik_id = ?";
        PreparedStatement statement = veza.prepareStatement(upit);
        statement.setInt(1, korisnik_id);
        ResultSet resultSet = statement.executeQuery();

        while (resultSet.next()) {
            String imeLokacije = resultSet.getString("naziv");
            locationComboBox.addItem(imeLokacije);
        }

    }

    // metoda za uklanjanje lokacije iz baze podataka
    private void ukloniLokacijuIzBaze(String imeLokacije, int korisnik_id) throws SQLException {
        String upit = "DELETE FROM Lokacije WHERE naziv = ? AND korisnik_id = ?";
        PreparedStatement statement = veza.prepareStatement(upit);
        statement.setString(1, imeLokacije);
        statement.setInt(2, korisnik_id);

        //izvrsi upit
        statement.executeUpdate();

    }


    // metoda za popunjavanje tablice s podacima iz baze podataka
    private void popuniTablicuIzBaze(int korisnik_id, int ulogaID) throws SQLException {

        // stvaranje bazena dretvi
        ExecutorService executor = Executors.newCachedThreadPool();
        // mutex za medusobno zakljucavanje
        ReentrantLock lock = new ReentrantLock();

        DefaultTableModel model = new DefaultTableModel(
                poljeZaglavlje,
                0);


        String upit = "SELECT naziv FROM Lokacije WHERE korisnik_id = ?";
        PreparedStatement statement = veza.prepareStatement(upit);
        statement.setInt(1, korisnik_id);
        ResultSet rezultatUpita = statement.executeQuery();

        while (rezultatUpita.next()) {
            String lokacija = rezultatUpita.getString("naziv");

            // dodavanje zadatka u executor
            executor.submit(() -> {
                PodaciOLokaciji podaciOLokaciji = dohvatiPodatkeAPI(lokacija, ulogaID);
                if (podaciOLokaciji != null) {
                    // zakljucavanje
                    lock.lock();
                    try {
                        //popunjavanje tablice
                        popuniRedakTablice(model, podaciOLokaciji);
                    } finally {
                        // otkljucavanje
                        lock.unlock();
                    }
                }
            });
        }

        // brisanje bazena dretvi
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // postavljanje modela na tablicu nakon sto su svi zadaci gotovi
        tablica.setModel(model);
        tablica.setShowGrid(false);
        tablica.setShowHorizontalLines(false);
        tablica.setShowVerticalLines(false);
        tablica.setIntercellSpacing(new Dimension(0, 0));
        tablica.setCellSelectionEnabled(false);
        tablica.getSelectionModel().clearSelection();
    }


    // Metoda za popunjavanje pojedinačnog retka u modelu tablice
    private void popuniRedakTablice(DefaultTableModel model, PodaciOLokaciji podaciOLokaciji) {
        // Dodaj red za trenutno vrijeme
        model.addRow(new Object[]{
                podaciOLokaciji.getLokacija(),
                podaciOLokaciji.getTrenutnaTemperatura() + "°C",
                podaciOLokaciji.getTrenutnePadaline() + "mm",
                podaciOLokaciji.getTrenutniVjetar() + "km/h",
                "",
                "",
                "",
                "",
                ""
        });

        // Dodaj redove za prognoze
        for (Prognoza prognoza : podaciOLokaciji.getPrognoze()) {
            model.addRow(new Object[]{
                    "",
                    "",
                    "",
                    "",
                    prognoza.getDan(),
                    prognoza.getMinTemperatura() + "°C",
                    prognoza.getMaxTemperatura() + "°C",
                    prognoza.getPadaline() + "mm",
                    prognoza.getVjetar() + "km/h"
            });
        }
    }

    private void dodajLokacijuUBazu(String lokacija, int korisnik_id) throws SQLException {
        String upit = "INSERT INTO Lokacije (naziv, korisnik_id) VALUES (?, ?)";
        PreparedStatement statement = veza.prepareStatement(upit);
        statement.setString(1, lokacija);
        statement.setInt(2, korisnik_id);

        // izvrsi upit
        statement.executeUpdate();

    }


    private void generirajIzvjesce(String korisnickoime) {
        try {
            // stvaranje novog PDF dokumenta
            PDDocument dokument = new PDDocument();
            PDPage stranica = new PDPage();
            dokument.addPage(stranica);

            // stvaranje novog toka za dodavanje sadržaja u PDF
            PDPageContentStream contentStream = new PDPageContentStream(dokument, stranica);

            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            // dodavanje korisnickog imena
            contentStream.beginText();
            contentStream.newLineAtOffset(100, 680);
            contentStream.showText(korisnickoime);
            contentStream.endText();

            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
            // dodavanje naslova
            contentStream.beginText();
            contentStream.newLineAtOffset(100, 700);
            contentStream.showText("Vremenska Prognoza");
            contentStream.endText();

            float margina = 10;
            float pozicijaY = 600;
            float visinaRedka = 20;

            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);

            // dohvacanje modela tablice
            DefaultTableModel model = (DefaultTableModel) tablica.getModel();

            // dodavanje zaglavlja tablice
            for (int i = 0; i < poljeZaglavlje.length; i++) {
                contentStream.beginText();
                contentStream.newLineAtOffset(margina + (i * 65), pozicijaY);
                contentStream.showText(poljeZaglavlje[i]);
                contentStream.endText();
            }
            pozicijaY -= visinaRedka;

            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            // upis podataka u retke
            for (int i = 0; i < model.getRowCount(); i++) {
                List<String> redak = dohvatiRedak(model, i);
                for (int j = 0; j < redak.size(); j++) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margina + (j * 65), pozicijaY);
                    contentStream.showText(redak.get(j));
                    contentStream.endText();
                }
                pozicijaY -= visinaRedka;
            }

            // zatvaranje toka
            contentStream.close();

            // spremanje PDF dokumenta
            File reportFile = new File("./report.pdf");
            if (reportFile.exists()) {
                Files.deleteIfExists(reportFile.toPath());
            }
            dokument.save(reportFile);

            // zatvaranje
            dokument.close();

            // prikaz izvjesca
            prikaziIzvjesce(reportFile);

            JOptionPane.showMessageDialog(null, izvjesceUspjehE, "Uspjeh", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    private void prikaziIzvjesce(File reportFile) {
        try {
            Desktop.getDesktop().open(reportFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, izvjesceNeuspjehE, "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    // metoda za dobivanje podataka određenog retka iz tablice
    private List<String> dohvatiRedak(DefaultTableModel model, int row) {
        List<String> vrijednostiRedka = new ArrayList<>();
        for (int i = 0; i < model.getColumnCount(); i++) {
            vrijednostiRedka.add(model.getValueAt(row, i).toString());
        }
        return vrijednostiRedka;
    }

    public static String enkriptiraj(String tekst, String kljuc) throws Exception {
        // stvaranje instance za kriptiranje i tajog kljuca
        Cipher cipher = Cipher.getInstance("AES");
        Key tajniKljuc = new SecretKeySpec(kljuc.getBytes("UTF-8"), "AES");

        cipher.init(Cipher.ENCRYPT_MODE, tajniKljuc);

        // enkripcija
        byte[] encryptedBytes = cipher.doFinal(tekst.getBytes("UTF-8"));

        // vracanje stringa
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }


    public static void main(String[] args) {
    }
}
