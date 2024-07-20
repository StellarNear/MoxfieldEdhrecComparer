package com.stellarnear;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Hello world!
 */
public final class Moxfieldedhreccomparer {

    private static CustomLog log = new CustomLog(Moxfieldedhreccomparer.class);

    private static boolean ollamaCategory = true;

    private static Map<String, String> cardCategories = new HashMap<>();

    private static int savedCard;

    private Moxfieldedhreccomparer() {
    }

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        long startTotal = System.currentTimeMillis();
       
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        UsersData usersData = mapper.readValue(new File("./users.yml"), UsersData.class);


        /* Setting part */
        boolean singleDeck = true;

        int percentRetainMissingCard = 75;

        // setup the rest if singl== true
        String name = "Orvar";
        for (User user : usersData.getUsers()) {
            for(Deck deck : user.getDecks()){
                if(deck.getName().equalsIgnoreCase(name)){
                    treatDeck(deck.getName(), deck.getMoxfieldId(), deck.getEdhreclinks(), percentRetainMissingCard);
                }
              
            }
        }
        /*  mannual settings
        String publicMoxfieldId = "xveOnGFOsE2A9eI1jmk6IQ";
         List<String> edhRecPages = new ArrayList<>();
        edhRecPages.add(
                "https://edhrec.com/_next/data/70Pv9HGGACsYuCcVwbdwE/commanders/feather-the-redeemed.json?slug=feather-the-redeemed");
                treatDec()
*/


       

        /* END Setting part */

        if (!singleDeck) {
            for (User user : usersData.getUsers()) {
                log.info("Treating user : "+user.getName()+ " having "+user.getDecks().size()+" decks.");
                for(Deck deck : user.getDecks()){
                    treatDeck(deck.getName(), deck.getMoxfieldId(), deck.getEdhreclinks(), percentRetainMissingCard);
                }
            }

            log.info("One deck to treat");
        }

        long endTotal = System.currentTimeMillis();
        log.info("MoxfieldEdhComparer ended it took a total time of " + convertTime(endTotal - startTotal));
    }

    private static void treatDeck(String name, String publicMoxfieldId, List<String> edhRecPages,

            int percentRetainMissingCard) throws IOException {
        long startDeck = System.currentTimeMillis();
        List<Card> allDeckCards = getDeckListFor(publicMoxfieldId);
        Map<String, Card> nameCardStat = new HashMap<>();
        for (String edhUrl : edhRecPages) {
            addCardFromEdhrec(nameCardStat, edhUrl);
        }

        allDeckCards.sort(Comparator.comparing(Card::getName));

        List<Card> crea = new ArrayList<>();
        List<Card> sorcery = new ArrayList<>();
        List<Card> instant = new ArrayList<>();
        List<Card> artifact = new ArrayList<>();
        List<Card> enchantment = new ArrayList<>();
        List<Card> land = new ArrayList<>();
        List<Card> misc = new ArrayList<>();
        List<Card> missing = new ArrayList<>();

        int nData = 0;
        Set<String> allDeckCardName = new HashSet<>();
        for (Card card : allDeckCards) {
            allDeckCardName.add(card.getName());
            if (nameCardStat.containsKey(card.getName())) {
                card.setnDeck(nameCardStat.get(card.getName()).getnDeck());
                card.setPercentPresentDeck(nameCardStat.get(card.getName()).getPercentPresentDeck());
                card.setSynergyPercent(nameCardStat.get(card.getName()).getSynergyPercent());
                nData++;
            } else {
                card.setnDeck(-1);
                card.setPercentPresentDeck(-1);
                card.setSynergyPercent(-1);
            }

            if (card.getType_line().toLowerCase().contains("creat")
                    && !card.getType_line().toLowerCase().contains("enchant")) {
                crea.add(card);
            } else if (card.getType_line().toLowerCase().contains("sorcer")) {
                sorcery.add(card);
            } else if (card.getType_line().toLowerCase().contains("instant")) {
                instant.add(card);
            } else if (card.getType_line().toLowerCase().contains("artifa")) {
                artifact.add(card);
            } else if (card.getType_line().toLowerCase().contains("enchant")) {
                enchantment.add(card);
            } else if (card.getType_line().toLowerCase().contains("land")) {
                land.add(card);
            } else {
                misc.add(card);
            }
        }

        for (Entry<String, Card> entry : nameCardStat.entrySet()) {
            if (!allDeckCardName.contains(entry.getKey())
                    && (entry.getValue().getSynergyPercent() >= percentRetainMissingCard
                            || entry.getValue().getPercentPresentDeck() >= percentRetainMissingCard)) {
                missing.add(entry.getValue());
            }
        }

        csvOutput(name, crea, "Creatures");

        csvOutput(name, sorcery, "Sorceries");

        csvOutput(name, instant, "Instants");

        csvOutput(name, artifact, "Artifacts");

        csvOutput(name, land, "Lands");

        csvOutput(name, enchantment, "Enchantments");

        csvOutput(name, misc, "Miscs");

        if (missing.size() > 0) {
            csvOutput(name, missing, "Missings");
            log.warn("We have " + missing.size() + " missing cards !");
        }
        long endDeck = System.currentTimeMillis();

        log.info("Deck [" + name + "] ended it took a total time of " + convertTime(endDeck - startDeck));
        log.info("Deck [" + name + "] We had " + allDeckCards.size() + " cards and found data on " + nData + " cards ("
                + ((int) (100.0 * ((1.0 * nData) / (1.0 * allDeckCards.size())))) + " %).");
    }

    private static void addCardFromEdhrec(Map<String, Card> mapNameCard, String edhUrl) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(edhUrl);
            connection = (HttpURLConnection) url.openConnection();
            // Set connection properties as needed
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            // Read response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                String jsonResponse = responseBuilder.toString();

                // Parse the JSON response
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                // Navigate to the "cardviews" arrays
                JsonNode cardlistsNode = rootNode.path("pageProps").path("data").path("container").path("json_dict")
                        .path("cardlists");
                if (cardlistsNode.isArray()) {
                    for (JsonNode cardlistNode : cardlistsNode) {
                        JsonNode cardviewsNode = cardlistNode.path("cardviews");
                        if (cardviewsNode.isArray()) {
                            for (JsonNode cardNode : cardviewsNode) {
                                String name = cleanString(cardNode.path("name").asText());

                                double synergy = cardNode.path("synergy").asDouble();

                                int numDecks = cardNode.path("num_decks").asInt();
                                int potentialDecks = cardNode.path("potential_decks").asInt();
                                int percent = (int) (100.0 * ((1.0 * numDecks) / (1.0 * potentialDecks)));
                                int synergPercent = (int) (1.0 * synergy * 100.0);

                                Card card = new Card(name, numDecks, percent, synergPercent);

                                if (mapNameCard.containsKey(name)) {
                                    Card stored = mapNameCard.get(name);
                                    int numDecksStored = stored.getnDeck();
                                    if (numDecks > numDecksStored) {
                                        stored.setnDeck(numDecks);
                                    }

                                    int percentStored = stored.getPercentPresentDeck();
                                    if (percent > percentStored) {
                                        stored.setPercentPresentDeck(percent);
                                    }

                                    int synergPercentStored = stored.getSynergyPercent();
                                    if (synergPercent > synergPercentStored) {
                                        stored.setSynergyPercent(synergPercent);
                                    }
                                } else {
                                    mapNameCard.put(name, card);
                                }

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<Card> getDeckListFor(String publicId) throws MalformedURLException {
        // ex https://api.moxfield.com/v2/decks/all/HxV33izihky7KTwjU0ER9w
        String deckUrl = "https://api.moxfield.com/v2/decks/all/" + publicId;
        URL url = new URL(deckUrl);
        HttpURLConnection connection;

        try {
            connection = (HttpURLConnection) url.openConnection();
            setConenction(connection);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                String jsonResponse = responseBuilder.toString();

                // Parse the JSON response
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                // Assuming "mainboard" is a direct child of the root node and contains the
                // cards
                JsonNode mainboardNode = rootNode.path("mainboard");

                Iterator<Map.Entry<String, JsonNode>> fields = mainboardNode.fields();
                List<Card> deckCards = new ArrayList<>();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode cardNode = entry.getValue().path("card");

                    String name = cleanString(cardNode.path("name").asText());

                    String rarity = cardNode.path("rarity").asText();
                    String mana_cost = cardNode.path("mana_cost").asText();
                    int cmc = cardNode.path("cmc").asInt();
                    String type_line = cardNode.path("type_line").asText();

                    String oracle_text = cardNode.path("oracle_text").asText();
                    List<String> color_identity = objectMapper.convertValue(cardNode.path("color_identity"),
                            new TypeReference<List<String>>() {
                            });

                    String commanderLegality = cardNode.path("legalities").path("commander").asText();
                    Card card = new Card(name, rarity, mana_cost, cmc, type_line, color_identity, commanderLegality,
                            oracle_text);
                    deckCards.add(card);
                }
                return deckCards;
            } catch (Exception e1) {
                e1.printStackTrace();
                log.err("Error reading the user data", e1);

            }
        } catch (Exception e) {
            log.err("Error getting the connection to user data", e);
        }
        return new ArrayList<>();
    }

    private static void setConenction(HttpURLConnection connection) {
        connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        // connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br, zstd");
        // connection.setRequestProperty("Accept-Language",
        // "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7");
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        // HERE go to the url and get the refresh from the header with dev mod on chrome
        connection.setRequestProperty("Cookie", "refresh_token=13c67b3b-3cf8-465a-aacd-3bb73f8c04f1");
    }

    public static String cleanString(String inputRaw) {

        String input;
        if (needsEncodingFix(inputRaw)) {
            byte[] bytes = inputRaw.getBytes(StandardCharsets.ISO_8859_1);
            input = new String(bytes, StandardCharsets.UTF_8);
        } else {
            input = inputRaw;
        }

        return input;

        /*
         * // Convert to lowercase
         * String lowerCaseString = input.toLowerCase();
         * 
         * // Normalize the string to decompose accents
         * String normalizedString = Normalizer.normalize(lowerCaseString,
         * Normalizer.Form.NFD);
         * 
         * // Remove accents
         * // String accentRemovedString =
         * normalizedString.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
         * 
         * String accentRemovedString = normalizedString.replaceAll("\\p{M}", "");
         * 
         * // Remove non-alphabetic characters except spaces
         * String cleanedString = accentRemovedString.replaceAll("[^a-zA-Z\\s]", "");
         * 
         * return cleanedString;
         */

    }

    public static boolean needsEncodingFix(String input) {
        // Check for common signs of encoding issues:
        // 1. Presence of sequences that are unlikely to be valid UTF-8
        // 2. Characters outside the typical printable ASCII and Latin-1 range

        for (char c : input.toCharArray()) {
            // Detect non-ASCII, non-printable characters, which could indicate encoding
            // issues
            if ((c < 32 || c > 126) && (c < 160 || c > 255)) {
                return true;
            }
        }

        return false;
    }

    private static void csvOutput(String nameDeck, List<Card> allCards, String nameFile) throws IOException {
        csvOutputFile(nameDeck, allCards, nameFile + "_alphabetical");
        allCards.sort(Comparator.comparing(Card::getPercentPresentDeck));
        csvOutputFile(nameDeck, allCards, nameFile + "_usage_percent");
    }

    private static void csvOutputFile(String nameDeck, List<Card> allCards, String nameFile) throws IOException {
        File deckDir = new File("OUT/" + nameDeck);
        if (!deckDir.exists()) {
            deckDir.mkdir();
        }
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(deckDir + "/" + nameFile + ".csv"), StandardCharsets.UTF_8))) {
            // CSV Header
            out.println("CardName;PercentPresence;Ndeck;Synergy");

            // Write each card's data
            for (Card card : allCards) {
                out.printf("\"%s\";%d;%d;%d%n",
                        card.getName(),
                        card.getPercentPresentDeck(),
                        card.getnDeck(),
                        card.getSynergyPercent());
            }
        }
    }

    private static String convertTime(long l) {
        int nHour = (int) (((l / 1000) / 60) / 60);
        if (nHour > 0) {
            int nMinute = (int) ((l / 1000) / 60) - 60 * nHour;
            return nHour + " hours " + nMinute + " minutes";
        } else {
            int nMinute = (int) ((l / 1000) / 60);
            if (nMinute > 0) {
                return nMinute + " minutes";
            } else {
                return (int) (l / 1000) + " seconds";
            }
        }
    }

}
