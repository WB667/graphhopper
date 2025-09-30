package com.graphhopper.navigation;

import org.junit.jupiter.api.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.util.TranslationMap;
import com.graphhopper.util.shapes.GHPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Test: testGetBearing_variousCases
 * But: valider le parsing de NavigateResource.getBearing(String).
 * Cas couverts & données :
 *  1) ""  → []            : aucun bearing → liste vide.
 *  2) "100,1;;200,0;"    : segments vides entre ";;" et en fin ";" → [100, NaN, 200, NaN].
 *  3) "10"               : pas de virgule → IllegalArgumentException.
 *  4) "abc,5"            : partie gauche non numérique → IllegalArgumentException.
 * Oracle:
 *  - valeurs numériques comparées avec tolérance (1e-12),
 *  - NaN vérifié avec isNaN,
 *  - exceptions attendues via assertThrows.
 * Couverture: branches de getBearing -> chaîne vide, segments vides, format invalide, NumberFormatException.
 */
    
    @Test
    void testGetBearing_variousCases() {
        // 1) Chaîne vide -> liste vide
        assertTrue(NavigateResource.getBearing("").isEmpty(), "Vide doit donner une liste vide");

        // 2) Segments valides + segments vides:
        //    - "100,1"     -> 100
        //    - "" (entre ;;)-> NaN
        //    - "200,0"     -> 200
        //    - "" (fin ;)  -> NaN
        List<Double> vals = NavigateResource.getBearing("100,1;;200,0;");
        assertEquals(4, vals.size(), "On doit avoir 4 entrées");
        assertEquals(100d, vals.get(0), 1e-12);
        assertTrue(vals.get(1).isNaN(), "Entrée vide => NaN");
        assertEquals(200d, vals.get(2), 1e-12);
        assertTrue(vals.get(3).isNaN(), "Entrée vide en fin => NaN");

        // 3) Erreur : pas de virgule -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> NavigateResource.getBearing("10"),
                "Pas de virgule : doit lever IllegalArgumentException");

        // 4) Erreur : non numérique -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> NavigateResource.getBearing("abc,5"),
                "Non numérique : doit lever IllegalArgumentException");
    }

  /**
 * Test : doGet_guardChecks
 * Intention : valider les 5 gardes initiaux de NavigateResource#doGet(..),
 *             chacun devant rejeter la requête quand l’option requise n’est pas respectée.
 *
 * Cas couverts (1 seul paramètre invalidé par sous-cas) :
 *   1) geometries = "polyline"  (≠ "polyline6")
 *   2) steps = false
 *   3) roundabout_exits = false
 *   4) voice_instructions = false
 *   5) banner_instructions = false
 *
 * Données & motivation :
 *   - httpReq / uriInfo / rc = null : sans risque, car les gardes sont évalués AVANT tout accès à ces objets.
 *   - Paramètres neutres pour isoler le garde testé : voiceUnits="metric", overview="simplified",
 *     bearings="", language="en", profile="driving", et tous les autres flags à true.
 *
 * Oracle (comment on décide que c’est bon) :
 *   - Pour chaque sous-cas : assertThrows(IllegalArgumentException.class).
 *   - Optionnel : vérifier le message caractéristique (p. ex. contient "polyline6",
 *     "enable steps", "roundabout exits", "voice instructions", "banner instructions")
 *     afin de s’assurer qu’on a bien frappé le *bon* garde.
 *   - Ce pattern garantit que ni le parsing avancé ni le routage ne sont atteints (échec immédiat).
 *
 * Couverture apportée :
 *   - Exécute doGet(..) jusqu’à l’exception pour chacun des 5 gardes → 5 branches « true »
 *     explicitement couvertes ; les autres paramètres à true parcourent implicitement les branches « false ».
 *   - Augmente la couverture d’instructions et de branches de doGet(..) sans dépendre d’un GraphHopper initialisé.
 */
    
@Test
public void doGet_guardChecks() {
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
 * Nom du test : doPost_requiresTypeMapbox
 *
 * Intention :
 *   Vérifier que NavigateResource#doPost(..) rejette une requête qui n’indique pas
 *   explicitement `type=mapbox` dans les hints (garde final de la méthode).
 *
 * Données (motivation) :
 *   - NavigateResource créé avec graphHopper=null (inutile ici : on échoue avant le routage).
 *   - GHRequest vierge (hints vides) → tous les premiers checks "Do not set X" passent en faux,
 *     puis on atteint le test `type=mapbox`.
 *   - httpReq=null : sûr car non utilisé avant l’exception.
 *
 * Oracle :
 *   - assertThrows(IllegalArgumentException.class) lors de l’appel à doPost(req, null).
 *   - Vérification du message contenant "type=mapbox" pour confirmer que le bon garde a échoué.
 *
 * Couverture :
 *   - Exécute doPost(..) depuis le début jusqu’au garde final `type=mapbox` (branche vraie),
 *     tout en parcourant les checks précédents sur leur branche « false ».
 *   - Augmente la couverture d’instructions et de branches de doPost(..) sans dépendre d’un graphe.
 */

@Test
public void doPost_requiresTypeMapbox() {
    // Pas besoin de GraphHopper ici: on échoue avant tout routage
    NavigateResource res = new NavigateResource(null, new TranslationMap(), new GraphHopperConfig());

    // Requête GH sans le hint "type=mapbox" -> doit lever IllegalArgumentException
    GHRequest req = new GHRequest(); // hints vides

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> res.doPost(req, null));

    // Message caractéristique du garde final
    assertTrue(ex.getMessage().contains("type=mapbox"), "Le message doit mentionner 'type=mapbox'");
}

}



