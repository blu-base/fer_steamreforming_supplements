/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tbr;

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.Report;
import star.vis.Displayer;
import star.vis.Scene;
import star.vis.SimpleAnnotation;

/**
 *
 * @author sengel
 */
public class postSim extends StarMacro {

    public void execute() {
        Simulation sim = getActiveSimulation();

        sim.println("Now running postSim.");

        Report yieldRep = sim.getReportManager().getReport("Yield_CO_outlet");
        double yield = yieldRep.getReportMonitorValue();

        SimpleAnnotation labelYield = ((SimpleAnnotation) sim.getAnnotationManager().getObject("Label_Yield"));
        labelYield.setText("YieldCO: " + yield);

        sim.println("Finished.");
        
           // Correcting Representation (Latest -> Volume Mesh)
        FvRepresentation fvRepresentation_0 = ((FvRepresentation) sim.getRepresentationManager().getObject("Volume Mesh"));
          Collection<SimpleAnnotation> annotationLabels = sim.getAnnotationManager().getObjectsOf(SimpleAnnotation.class);
      
        
        
        for(Scene sce : sim.getSceneManager().getScenes()) {
            for (Displayer dspl : sce.getDisplayerManager().getObjects()) {
                dspl.setRepresentation(fvRepresentation_0);
            }
            
            sce.getAnnotationPropManager().removePropsForAnnotations();

            annotationLabels.forEach(
                    label -> sce.getAnnotationPropManager()
                            .createPropForAnnotation(label)
            );           
            
            
            
        }
        
    }

}
