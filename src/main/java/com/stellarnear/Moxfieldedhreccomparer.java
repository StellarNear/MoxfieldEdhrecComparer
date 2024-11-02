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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Hello world!
 */
public final class Moxfieldedhreccomparer {

    private static CustomLog log = new CustomLog(Moxfieldedhreccomparer.class);
    private static String edhRecToken;
    private static int percentRetainMissingCard;
    private static boolean doHistory = true;
        private static Integer daysHistory=30;
    
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
            edhRecToken = getTokenFromEdhRec();
            if (edhRecToken == null) {
                log.warn("Could not parse token !");
                return;
            }
    
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            UsersData usersData = mapper.readValue(new File("./users.yml"), UsersData.class);
    
            /* Setting part */
            percentRetainMissingCard = 40;
    
            // if single from full list
            boolean singleDeckFromList = false;
            String deckName = "yuriko";
    
            // manual
            boolean manualSetting = false;
            // deckName = "Frodo";
            String publicMoxfieldId = "8oLyoc0ekiJegXH_pWNJ9";
            List<String> edhRecPages = new ArrayList<>();
            edhRecPages.add(
                    "https://edhrec.com/_next/data/GHPzhXhQMBhgMPrgEUOVI/commanders/frodo-adventurous-hobbit-sam-loyal-attendant.json?slug=frodo-adventurous-hobbit-sam-loyal-attendant");
            edhRecPages.add(
                    "https://edhrec.com/_next/data/GHPzhXhQMBhgMPrgEUOVI/commanders/frodo-adventurous-hobbit-sam-loyal-attendant/lifegain.json?slug=frodo-adventurous-hobbit-sam-loyal-attendant&themeName=lifegain");
            /* enf of settign if all false we do all */
    
            if (manualSetting) {
                treatDeck("Custom", deckName, publicMoxfieldId, edhRecPages);
            } else if (singleDeckFromList) {
    
                for (User user : usersData.getUsers()) {
                    for (Deck deck : user.getDecks()) {
                        if (deck.getName().equalsIgnoreCase(deckName)) {
                            treatDeck(user.getName(), deck.getName(), deck.getMoxfieldId(), deck.getEdhreclinks());
                        }
                    }
                }
            } else {
                for (User user : usersData.getUsers()) {
                    log.info("Treating user : " + user.getName() + " having " + user.getDecks().size() + " decks.");
    
                    HashMap<String,List<CardAddition>> mapNnewCardDeckName = new HashMap<>();
                    for (Deck deck : user.getDecks()) {
                        treatDeck(user.getName(), deck.getName(), deck.getMoxfieldId(), deck.getEdhreclinks());
    
                        if (doHistory) {
                            List<CardAddition> listAddition = getCardAdditionsHistory(deck.getMoxfieldId());
                            Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(daysHistory));
    
                            List<CardAddition> nNewCardMonth = listAddition.stream()
                                                            .filter(cardAddition -> cardAddition.getUpdatedAtUtc().isAfter(thirtyDaysAgo))
                                                            .collect(Collectors.toList());
    
                            mapNnewCardDeckName.put(deck.getName(), nNewCardMonth);
    
                        }
    
                    }
                    if(mapNnewCardDeckName.size()>0){
                        csvHistoryOutputFile(user.getName(), mapNnewCardDeckName);
                    }
                }
            }
    
            long endTotal = System.currentTimeMillis();
            log.info("MoxfieldEdhComparer ended it took a total time of " + convertTime(endTotal - startTotal));
        }
    
        private static String getTokenFromEdhRec() {
            log.info("Getting token from EDHrec");
            HttpURLConnection connection = null;
            String edhUrl = "https://edhrec.com/";
    
            try {
                // Create a connection
                URL url = new URL(edhUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/html");
    
                // Read response
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
    
                // Extract HTML content as string
                String htmlContent = responseBuilder.toString();
    
                // Extract the JSON string from the <script> tag with id="__NEXT_DATA__"
                String json = extractJsonData(htmlContent);
                if (json != null) {
                    log.info("Extracted JSON!");
                    log.info("Searching token...");
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode rootNode = objectMapper.readTree(json);
    
                    // Extract the buildId value
                    String buildId = rootNode.get("buildId").asText();
                    log.info("Token found : " + buildId);
                    return buildId;
                } else {
                    log.info("No JSON data found!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }
    
        private static String extractJsonData(String htmlContent) {
            // Use regex to find the <script> tag with id="__NEXT_DATA__"
            Pattern pattern = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
                    Pattern.DOTALL);
            Matcher matcher = pattern.matcher(htmlContent);
    
            if (matcher.find()) {
                // Return the JSON string
                return matcher.group(1);
            }
            return null;
        }
    
        private static void treatDeck(String user, String name, String publicMoxfieldId, List<String> edhRecPages)
                throws IOException {
            log.info("Treating deck " + name);
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
    
            csvOutput(user, name, crea, "Creatures");
    
            csvOutput(user, name, sorcery, "Sorceries");
    
            csvOutput(user, name, instant, "Instants");
    
            csvOutput(user, name, artifact, "Artifacts");
    
            csvOutput(user, name, land, "Lands");
    
            csvOutput(user, name, enchantment, "Enchantments");
    
            csvOutput(user, name, misc, "Miscs");
    
            csvOutput(user, name, allDeckCards, "AllCards");
    
            if (missing.size() > 0) {
                csvOutput(user, name, missing, "Missings");
                log.warn("We have " + missing.size() + " missing cards !");
            }
            long endDeck = System.currentTimeMillis();
    
            log.info("Deck [" + name + "] ended it took a total time of " + convertTime(endDeck - startDeck));
            log.info("Deck [" + name + "] We had " + allDeckCards.size() + " cards and found data on " + nData + " cards ("
                    + ((int) (100.0 * ((1.0 * nData) / (1.0 * allDeckCards.size())))) + " %).");
        }
    
        private static void addCardFromEdhrec(Map<String, Card> mapNameCard, String oldEdhUrl) {
            HttpURLConnection connection = null;
    
            try {
    
                String regex = "(?<=_next/data/)(.*?)(?=/commanders)";
                String edhUrl = oldEdhUrl.replaceAll(regex, edhRecToken);
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
                                    if (name.contains("//")) {
                                        name = name.split("//")[0].trim();
                                    }
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
    
                    if (rootNode.path("mainboardCount").asInt() > 99) {
                        log.warn("The mainboard has " + rootNode.path("mainboardCount").asInt()
                                + " cards !! THAT IS NOT LEGAL");
                    }
    
                    Iterator<Map.Entry<String, JsonNode>> fields = mainboardNode.fields();
    
                    List<Card> deckCards = new ArrayList<>();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
    
                        JsonNode cardNode = entry.getValue().path("card");
    
                        String name = cleanString(cardNode.path("name").asText());
                        if (name.contains("//")) {
                            name = name.split("//")[0].trim();
                        }
    
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
             * normalizedString.replaceAll("\p{InCombiningDiacriticalMarks}+", "");
             * 
             * String accentRemovedString = normalizedString.replaceAll("\p{M}", "");
             * 
             * // Remove non-alphabetic characters except spaces
             * String cleanedString = accentRemovedString.replaceAll("[^a-zA-Z\s]", "");
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
    
        private static void csvOutput(String user, String nameDeck, List<Card> allCards, String nameFile)
                throws IOException {
            csvOutputFile(user, nameDeck, allCards, nameFile + "_alphabetical");
            allCards.sort(Comparator.comparing(Card::getPercentPresentDeck));
            csvOutputFile(user, nameDeck, allCards, nameFile + "_usage_percent");
        }
    
        private static void csvOutputFile(String user, String nameDeck, List<Card> allCards, String nameFile)
                throws IOException {
    
            File userDir = new File("OUT/" + user);
            if (!userDir.exists()) {
                userDir.mkdir();
            }
            File deckDir = new File(userDir.getAbsolutePath() + "/" + nameDeck);
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
    
        private static void csvHistoryOutputFile(String user, HashMap<String, List<CardAddition>> mapNnewCardDeckName) throws IOException {
            File userDir = new File("OUT/" + user);
            if (!userDir.exists()) {
                userDir.mkdir();
            }
        
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(userDir + "/nNewCard"+daysHistory+"days.csv"), StandardCharsets.UTF_8))) {
            // CSV Header
            out.println("DeckName;CardCount;CardNames");
    
            // Write each deck's data
            for (String deckName : mapNnewCardDeckName.keySet()) {
                List<CardAddition> cardAdditions = mapNnewCardDeckName.get(deckName);
                int cardCount = cardAdditions.size();
                
                // Join all card names with the '|' symbol
                String cardNames = cardAdditions.stream()
                        .map(CardAddition::getName)
                        .collect(Collectors.joining(" | "));
    
                out.printf("\"%s\";%d;\"%s\"%n", deckName, cardCount, cardNames);
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

    /*
     * For the history
     */
    public static List<CardAddition> getCardAdditionsHistory(String publicId) throws MalformedURLException {
        List<CardAddition> allCardAdditions = new ArrayList<>();
        int currentPage = 1;
        int totalPages=1;
    
        do {
            String historyUrl = "https://api.moxfield.com/v2/decks/all/" + publicId + "/history?pageSize=100&pageNumber=" + currentPage;
            URL url = new URL(historyUrl);
            HttpURLConnection connection;
    
            try {
                connection = (HttpURLConnection) url.openConnection();
                setConenction(connection);
    
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    String jsonResponse = responseBuilder.toString();
    
                    // Parse the JSON response
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode rootNode = objectMapper.readTree(jsonResponse);
    
                    // Retrieve totalPages if this is the first page
                    if (currentPage == 1) {
                        totalPages = rootNode.path("totalPages").asInt();
                    }
    
                    JsonNode dataNode = rootNode.path("data");
    
                    // Parse and filter cards
                    for (JsonNode entryNode : dataNode) {
                        String boardType = entryNode.path("boardType").asText();
                        int quantityDelta = entryNode.path("quantityDelta").asInt();
    
                        if ("mainboard".equals(boardType) && quantityDelta == 1) {
                            JsonNode cardNode = entryNode.path("card");
                            String name = cleanString(cardNode.path("name").asText());
    
                            // Parse updatedAtUtc as an Instant
                            Instant updatedAtUtc = Instant.parse(entryNode.path("updatedAtUtc").asText());
    
                            // Create a new CardAddition object and add it to the list
                            CardAddition cardAddition = new CardAddition(name, updatedAtUtc);
                            allCardAdditions.add(cardAddition);
                        }
                    }
                    currentPage++;
                } catch (Exception e1) {
                    e1.printStackTrace();
                    log.err("Error reading the card additions history data on page " + currentPage, e1);
                    break;
                }
            } catch (Exception e) {
                log.err("Error getting the connection to card additions history on page " + currentPage, e);
                break;
            }
        } while (currentPage <= totalPages);
    
        return allCardAdditions;
    }
    

}
