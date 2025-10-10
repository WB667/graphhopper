package com.graphhopper.navigation;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.util.TranslationMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NavigateResourceTest {
    @Test
    public void voiceInstructionsTest() {
        List<Double> bearings = NavigateResource.getBearing("");
        assertEquals(0, bearings.size());
        assertEquals(Collections.EMPTY_LIST, bearings);

        bearings = NavigateResource.getBearing("100,1");
        assertEquals(1, bearings.size());
        assertEquals(100, bearings.get(0), .1);

        bearings = NavigateResource.getBearing(";100,1;;");
        assertEquals(4, bearings.size());
        assertEquals(100, bearings.get(1), .1);
    }

    /**
     * getBearing_parsingTest <p>
     * BUT: valider le parsing de `getBearing()`. <p>
     * CAS: <p>
     *   1) "" → []        : aucun bearing → liste vide. <p>
     *   2) "100,1;;200,0;": segments vides entre ";;" et ";" à la fin → [100, NaN, 200, NaN]. <p>
     *   3) "10"           : pas de virgule → `IllegalArgumentException`. <p>
     *   4) "abc,5"        : partie gauche non numérique → `IllegalArgumentException`. <p>
     * ORACLE: <p>
     *   - valeurs numériques comparées avec tolérance (1e-12) <p>
     *   - `NaN` vérifié avec `isNaN()` <p>
     *   - exceptions attendues via `assertThrows` <p>
     * COUVERTURE: branches de `getBearing()` → chaîne vide, segments vides, format invalide, NumberFormatException. <p>
     * MUTANTS: Ce test ne détecte pas de nouveaux mutants.
     */
    @Test
    public void getBearing_parsingTest() {
        // 1) Chaîne vide → liste vide
        assertTrue(NavigateResource.getBearing("").isEmpty(), "Vide doit donner une liste vide");

        // 2) Segments valides + segments vides:
        //    - "100,1"       → 100
        //    - "" (entre ;;) → NaN
        //    - "200,0"       → 200
        //    - "" (fin ;)    → NaN
        List<Double> vals = NavigateResource.getBearing("100,1;;200,0;");
        assertEquals(4, vals.size(), "On doit avoir 4 entrées");
        assertEquals(100d, vals.get(0), 1e-12);
        assertTrue(vals.get(1).isNaN(), "Entrée vide => NaN");
        assertEquals(200d, vals.get(2), 1e-12);
        assertTrue(vals.get(3).isNaN(), "Entrée vide en fin => NaN");

        // 3) Erreur: pas de virgule: IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> NavigateResource.getBearing("10"),
                "Pas de virgule: doit lever IllegalArgumentException");

        // 4) Erreur: non numérique: IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> NavigateResource.getBearing("abc,5"),
                "Non numérique: doit lever IllegalArgumentException");
    }

    /**
     * doGet_guardsTest <p>
     * BUT: valider les 5 gardes initiaux de `doGet()`. <p>
     * CAS:
     *   1) geometries = "polyline" et pas "polyline6". <p>
     *   2) steps = false <p>
     *   3) roundabout_exits = false <p>
     *   4) voice_instructions = false <p>
     *   5) banner_instructions = false <p>
     * DONNÉES: <p>
     *   - httpReq / uriInfo / rc = null: sans risque, car les gardes sont évalués AVANT tout accès à ces objets. <p>
     *   - Paramètres neutres pour isoler le garde testé: voiceUnits="metric", overview="simplified",
     *     bearings="", language="en", profile="driving", et tous les autres flags à `true`. <p>
     * ORACLE: <p>
     *   - Pour chaque sous-cas: `assertThrows(IllegalArgumentException.class)`. <p>
     *   - Optionnel: vérifier le message caractéristique (ex. contient "polyline6", "enable steps",
     *     "roundabout exits", etc.) afin de s'assurer qu'on a bien frappé le *bon* garde. <p>
     *   - Ce pattern garantit que ni le parsing avancé ni le routage ne sont atteints (échec immédiat). <p>
     * COUVERTURE: <p>
     *   - Exécute `doGet()` jusqu'à l'exception pour chacun des 5 gardes → 5 branches `true`
     *     explicitement couvertes; les autres paramètres à `true` parcourent implicitement les branches `false`. <p>
     *   - Augmente la couverture d'instructions et de branches de `doGet()` sans dépendre d'un GraphHopper initialisé. <p>
     * MUTANTS: On détecte les mutants triviaux qui font échouer le test.
     */
    @Test
    public void doGet_guardsTest() {
        NavigateResource res = new NavigateResource(null, new TranslationMap(), new GraphHopperConfig());

        // 1) geometries != polyline6
        assertThrows(IllegalArgumentException.class, () ->
            res.doGet(null, null, null, true,  true,  true,  true,
                      "metric", "simplified", "polyline", "", "en", "driving")
        );

        // 2) steps = false
        assertThrows(IllegalArgumentException.class, () ->
            res.doGet(null, null, null, false, true,  true,  true,
                      "metric", "simplified", "polyline6", "", "en", "driving")
        );

        // 3) roundabout_exits = false
        assertThrows(IllegalArgumentException.class, () ->
            res.doGet(null, null, null, true,  true,  true,  false,
                      "metric", "simplified", "polyline6", "", "en", "driving")
        );

        // 4) voice_instructions = false
        assertThrows(IllegalArgumentException.class, () ->
            res.doGet(null, null, null, true,  false, true,  true,
                      "metric", "simplified", "polyline6", "", "en", "driving")
        );

        // 5) banner_instructions = false
        assertThrows(IllegalArgumentException.class, () ->
            res.doGet(null, null, null, true,  true,  false, true,
                      "metric", "simplified", "polyline6", "", "en", "driving")
        );
    }

    /**
     * doPost_requiresTypeMapbox <p>
     * BUT: Vérifier que `doPost()` rejette une requête qui n'indique pas
     *      explicitement `type=mapbox` dans les hints (garde final de la méthode). <p>
     * DONNÉES: <p>
     *   - `NavigateResource` créée avec `graphHopper=null` (inutile ici: on échoue avant le routage). <p>
     *   - GHRequest vierge (hints vides) → tous les premiers checks "Do not set X" passent en faux,
     *     puis on atteint le test `type=mapbox`. <p>
     *   - httpReq=null: sûr car non utilisé avant l'exception. <p>
     * ORACLE: <p>
     *   - `assertThrows(IllegalArgumentException.class)` lors de l'appel à `doPost(req, null)`. <p>
     *   - Vérification du message contenant `type=mapbox` pour confirmer que le bon garde a échoué. <p>
     * COUVERTURE: <p>
     *   - Exécute `doPost()` depuis le début jusqu'au garde final `type=mapbox` (branche vraie),
     *     tout en parcourant les checks précédents sur leur branche "false". <p>
     *   - Augmente la couverture d'instructions et de branches de `doPost()` sans dépendre d'un graphe. <p>
     * MUTANTS: On détecte le mutant trivial qui fait échouer le test.
     */
    @Test
    public void doPost_requiresTypeMapbox() {
        // Pas besoin de GraphHopper ici: on échoue avant tout routage
        NavigateResource res = new NavigateResource(null, new TranslationMap(), new GraphHopperConfig());

        // Requête GH sans le hint "type=mapbox": doit lever IllegalArgumentException
        GHRequest req = new GHRequest(); // hints vides

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> res.doPost(req, null));

        // Message caractéristique du garde final
        assertTrue(ex.getMessage().contains("type=mapbox"), "Le message doit mentionner 'type=mapbox'");
    }

    /**
     * Paramètres Mapbox interdits <p>
     * BUT: Vérifier que l'existence de paramètres Mapbox lance une exception. <p>
     * DONNÉES: Le nom des 10 paramètres Mapbox interdits. <p>
     * ORACLE: <p>
     *   - `doPost()` doit lancer l'expcetion `IllegalArgumentException`. <p>
     *   - Le message d'erreur doit inclure le nom du paramètre. <p>
     * COUVERTURE: Couvre les gardes au début de `doPost()`. <p>
     * MUTANTS: On détecte les mutants triviaux qui font échouer le test.
     */
    @ParameterizedTest
    @ValueSource(strings = {"geometries", "steps", "roundabout_exits", "voice_instructions", "banner_instructions", "elevation", "overview", "language", "points_encoded", "points_encoded_multiplier"})
    public void doPost_guardsTest(String field) {
        NavigateResource res = new NavigateResource(null, new TranslationMap(), new GraphHopperConfig());
        GHRequest req = new GHRequest();
        req.putHint(field, field);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> res.doPost(req, null));
        assertTrue(ex.getMessage().contains(field), "Le message doit mentionner '" + field + "'");
    }
}



