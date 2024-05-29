import com.formdev.flatlaf.FlatLightLaf;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;

public class Admin extends JFrame {

    private JComboBox<String> comboBoxTablica;
    private JTable tablicaPodataka;
    private JButton gumbDodaj;
    private JButton gumbBrisi;
    private JButton gumbAzuriraj;
    private JPanel adminForm;

    private Connection veza;

    String redakE;
    String redakBrisiE;
    String redakAzurirajE;
    String fetchE;
    Locale defLocale;

    public Admin(Connection veza, int ulogaID, Locale defLocale_) {
        this.veza = veza;
        FlatLightLaf.setup();

        defLocale = defLocale_;

        postaviUI();

        setTitle("Admin");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // prikaz statusa posluzitelja
        String status = dohvatiStatusAPI(ulogaID);
        if (status != null) {
            JOptionPane.showMessageDialog(this, status, "Info", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Error", "fetchE", JOptionPane.ERROR_MESSAGE);
        }

    }

    // ucitavanje svih tablica u combobox
    private void ucitajTablice() {
        try {
            DatabaseMetaData metaData = veza.getMetaData();
            ResultSet tablice = metaData.getTables(null, null, null, new String[]{"TABLE"});

            Vector<String> imenaTablica = new Vector<>();
            while (tablice.next()) {
                String imeTablice = tablice.getString("TABLE_NAME");
                imenaTablica.add(imeTablice);
            }

            if (comboBoxTablica != null) {
                comboBoxTablica.setModel(new DefaultComboBoxModel<>(imenaTablica));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // postavljanje komponenta sucelja
    private void postaviUI() {
        JPanel panel = new JPanel(new BorderLayout());

        // inicijalizacija elemenata
        gumbDodaj = new JButton();
        gumbBrisi = new JButton();
        gumbAzuriraj = new JButton();
        comboBoxTablica = new JComboBox<>();

        //postavljanje jezika
        ResourceBundle defBundle = ResourceBundle.getBundle("bundle", defLocale);

        gumbDodaj.setText(defBundle.getString("gumbDodaj"));
        gumbBrisi.setText(defBundle.getString("gumbBrisi"));
        gumbAzuriraj.setText(defBundle.getString("gumbAzuriraj"));
        redakE = defBundle.getString("redakE");
        redakBrisiE = defBundle.getString("redakBrisiE");
        redakAzurirajE = defBundle.getString("redakAzurirajE");
        fetchE = defBundle.getString("fetchE");

        //dodavanje tablica u combobox
        ucitajTablice();

        // listener za ucitavanje odabrane tablice
        comboBoxTablica.addActionListener(e -> ucitajPodatkeTablice((String) comboBoxTablica.getSelectedItem()));

        // listeneri za gumbe
        gumbDodaj.addActionListener(e -> dodajRed());
        gumbBrisi.addActionListener(e -> brisiRed());
        gumbAzuriraj.addActionListener(e -> azurirajRed());

        // dodavanje gumba na sucelje
        JPanel kontrolniPanel = new JPanel(new FlowLayout());
        kontrolniPanel.add(comboBoxTablica);
        kontrolniPanel.add(gumbDodaj);
        kontrolniPanel.add(gumbBrisi);
        kontrolniPanel.add(gumbAzuriraj);

        // dodavanje kontrolnog panela na glavni panel
        panel.add(kontrolniPanel, BorderLayout.NORTH);

        // inicijalizacija tablice podataka i dodavanje na panel
        tablicaPodataka = new JTable();
        panel.add(new JScrollPane(tablicaPodataka), BorderLayout.CENTER);

        // sortiranje
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(tablicaPodataka.getModel());
        tablicaPodataka.setRowSorter(sorter);
        tablicaPodataka.setAutoCreateRowSorter(true);

        // text polje za pretrazivanje
        JTextField poljePretrage = new JTextField(10);
        poljePretrage.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filtrirajPodatke(poljePretrage.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filtrirajPodatke(poljePretrage.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filtrirajPodatke(poljePretrage.getText());
            }
        });

        // dodavanje text polja na kontrolni panel
        kontrolniPanel.add(poljePretrage);

        // postavi panel kao sadrzaj prozora
        setContentPane(panel);
    }

    // ucitavanje podataka za tablicu iz baze
    private void ucitajPodatkeTablice(String imeTablice) {
        try {
            Statement statement = veza.createStatement();
            ResultSet rezultatSet = statement.executeQuery("SELECT * FROM " + imeTablice);
            ResultSetMetaData metaData = rezultatSet.getMetaData();

            int brojStupaca = metaData.getColumnCount();
            Vector<String> imenaStupaca = new Vector<>();
            for (int i = 1; i <= brojStupaca; i++) {
                imenaStupaca.add(metaData.getColumnName(i));
            }

            Vector<Vector<Object>> podaci = new Vector<>();
            while (rezultatSet.next()) {
                Vector<Object> redak = new Vector<>();
                for (int i = 1; i <= brojStupaca; i++) {
                    if (metaData.getColumnName(i).equals("uloga_id")) {
                        int ulogaId = rezultatSet.getInt("uloga_id");
                        String uloga = fetchUloga(ulogaId);
                        redak.add(uloga);
                    } else {
                        redak.add(rezultatSet.getObject(i));
                    }
                }
                podaci.add(redak);
            }

            DefaultTableModel model = new DefaultTableModel(podaci, imenaStupaca);
            tablicaPodataka.setModel(model);

            rezultatSet.close();
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String dohvatiStatusAPI(int ulogaID) {
        try {
            // konstruiranje urla
            String apiUrl = "http://localhost:8080/admin";
            URL url = new URL(apiUrl);

            // otvaranje veze
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // postavljanje zaglavlja
            conn.setRequestProperty("api-kljuc", Glavni.enkriptiraj("c3Sr9%#9#Y#]gQj@y$_*8", "0123456789abcdef"));
            conn.setRequestProperty("uloga", String.valueOf(ulogaID));

            // provjera odgovora
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // citanje odgovora
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close();

                // zatvaranje veze
                conn.disconnect();

                // parsiranje
                String jsonOdgovor = responseBuilder.toString();
                JSONObject jsonObjekt = new JSONObject(jsonOdgovor);

                // dohvat podataka o resursima
                String CPUuskoristenje = jsonObjekt.getString("cpu");
                String RAMiskoristenje = jsonObjekt.getString("ram");

                // formatiranje
                return "[SERVER STATUS]\nCPU: " + CPUuskoristenje + "\nRAM: " + RAMiskoristenje;
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

    //dohvacanje naziva uloge na temelju vanjskog kljuca uloga_id
    private String fetchUloga(int ulogaId) {
        try {
            String upit = "SELECT naziv FROM Uloge WHERE id = ?";
            PreparedStatement preparedStatement = veza.prepareStatement(upit);
            preparedStatement.setInt(1, ulogaId);
            ResultSet rezultatUpita = preparedStatement.executeQuery();
            if (rezultatUpita.next()) {
                return rezultatUpita.getString("naziv");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    // filtraranje podataki u tablici
    private void filtrirajPodatke(String filterTekst) {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) tablicaPodataka.getRowSorter();
        sorter.setRowFilter(RowFilter.regexFilter(filterTekst));
    }

    //dodvanje novog redka
    private void dodajRed() {
        try {
            String imeTablice = (String) comboBoxTablica.getSelectedItem();

            //dohvacanje imena stupaca
            DatabaseMetaData metaData = veza.getMetaData();
            ResultSet stupci = metaData.getColumns(null, null, imeTablice, null);

            // mapa za pohranu imena stupaca i teksta za dodavanje u red
            Map<String, JTextField> poljaTekstaMapa = new HashMap<>();
            JPanel panelUnosa = new JPanel(new GridLayout(0, 2));

            // iteracija kroz stupce i stvaranje tekstualnih polja za svaki stupac
            while (stupci.next()) {
                String imeStupca = stupci.getString("COLUMN_NAME");
                JTextField poljeTeksta = new JTextField(10);
                panelUnosa.add(new JLabel(imeStupca + ":"));
                panelUnosa.add(poljeTeksta);
                poljaTekstaMapa.put(imeStupca, poljeTeksta);
            }

            int rezultat = JOptionPane.showConfirmDialog(null, panelUnosa, redakE, JOptionPane.OK_CANCEL_OPTION);
            if (rezultat == JOptionPane.OK_OPTION) {
                // dohvacanje unesenih vrijednosti
                StringJoiner imenaStupaca = new StringJoiner(", ");
                StringJoiner vrijednostiStupaca = new StringJoiner(", ");
                //iteracija kroz svaki kjuc mape
                for (String kljuc : poljaTekstaMapa.keySet()) {
                    imenaStupaca.add(kljuc);
                    vrijednostiStupaca.add("?");
                }

                // insert upit za dodavanje novog reda
                String upitZaUnos = "INSERT INTO " + imeTablice + " (" + imenaStupaca + ") VALUES (" + vrijednostiStupaca + ")";
                PreparedStatement preparedStatement = veza.prepareStatement(upitZaUnos);
                int indeksParametra = 1;
                //iteracija kroz text polja iz mape
                for (JTextField poljeTeksta : poljaTekstaMapa.values()) {
                    preparedStatement.setString(indeksParametra++, poljeTeksta.getText());
                }

                // zatvaranje upita
                preparedStatement.executeUpdate();
                preparedStatement.close();

                // osvjezavanje podataka tablice
                ucitajPodatkeTablice(imeTablice);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // metoda za brisanje reda
    private void brisiRed() {
        DefaultTableModel model = (DefaultTableModel) tablicaPodataka.getModel();
        int odabraniRedak = tablicaPodataka.getSelectedRow();
        if (odabraniRedak != -1) {
            try {
                String imeTablice = (String) comboBoxTablica.getSelectedItem();
                String primarniKljuc = dohvatiPrimarniKljuc(imeTablice);
                Object vrijednostPrimarnogKljuca = model.getValueAt(odabraniRedak, model.findColumn(primarniKljuc));

                String upitBrisanja = "DELETE FROM " + imeTablice + " WHERE " + primarniKljuc + " = ?";
                PreparedStatement preparedStatement = veza.prepareStatement(upitBrisanja);
                preparedStatement.setObject(1, vrijednostPrimarnogKljuca);
                preparedStatement.executeUpdate();

                model.removeRow(odabraniRedak);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, redakBrisiE, "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    // mezoda za uredivanje redka
    private void azurirajRed() {
        int odabraniRedak = tablicaPodataka.getSelectedRow();
        if (odabraniRedak != -1) {
            try {
                String imeTablice = (String) comboBoxTablica.getSelectedItem();
                DefaultTableModel model = (DefaultTableModel) tablicaPodataka.getModel();
                int brojStupaca = model.getColumnCount();
                String primarniKljuc = dohvatiPrimarniKljuc(imeTablice);
                Object vrijednostPrimarnogKljuca = model.getValueAt(odabraniRedak, model.findColumn(primarniKljuc));

                StringBuilder graditeljUpita = new StringBuilder("UPDATE ").append(imeTablice).append(" SET ");

                for (int i = 0; i < brojStupaca; i++) {
                    String imeStupca = model.getColumnName(i);
                    graditeljUpita.append(imeStupca).append(" = ?, ");
                }
                graditeljUpita.delete(graditeljUpita.length() - 2, graditeljUpita.length());
                graditeljUpita.append(" WHERE ").append(primarniKljuc).append(" = ?");

                PreparedStatement preparedStatement = veza.prepareStatement(graditeljUpita.toString());

                for (int i = 0; i < brojStupaca; i++) {
                    Object vrijednostStupca = model.getValueAt(odabraniRedak, i);
                    preparedStatement.setObject(i + 1, vrijednostStupca);
                }
                preparedStatement.setObject(brojStupaca + 1, vrijednostPrimarnogKljuca);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, redakAzurirajE, "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }


    // dohvacanje primarnog kljuca odabranog reda
    private String dohvatiPrimarniKljuc(String imeTablice) {
        try {
            DatabaseMetaData metaData = veza.getMetaData();
            ResultSet primarniKljucevi = metaData.getPrimaryKeys(null, null, imeTablice);
            if (primarniKljucevi.next()) {
                return primarniKljucevi.getString("COLUMN_NAME");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
    }
}
