// STAR-CCM+ macro: remesh.java
// Written by STAR-CCM+ 13.02.013
package macro;

import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.sweptmesher.ui.*;
import star.cadmodeler.*;
import star.flow.ConvectiveFluxOption;
import star.flow.FlowUpwindOption;
import star.vis.*;
import star.meshing.*;
import star.segregatedenergy.SegregatedFluidTemperatureModel;
import star.segregatedflow.SegregatedFlowModel;
import star.segregatedspecies.SegregatedSpeciesModel;
import star.sweptmesher.*;

public class remesh extends StarMacro {

    public void execute() {
        solve();
    }

    private void solve() {

        Simulation sim
                = getActiveSimulation();

        setDefaultAnnotations();

        setMesh(0.05, 0.05);
        setFirstOrderConvection();
        for (int i = 0; i < 3; i++) {
            try {
                sim.getSimulationIterator().step(100);
            } catch (Exception e) {
                sim.println(e.toString());
            }
        }

        setMesh(0.1, 0.1);
        setFirstOrderConvection();
        for (int i = 0; i < 3; i++) {
            try {
                sim.getSimulationIterator().step(200);
            } catch (Exception e) {
                sim.println(e.toString());
            }
        }

        setMesh(0.5, 0.5);
        setFirstOrderConvection();
        for (int i = 0; i < 3; i++) {
            try {
                sim.getSimulationIterator().step(500);
            } catch (Exception e) {
                sim.println(e.toString());
            }
        }

        setSecondOrderConvection();
        for (int i = 0; i < 3; i++) {
            try {
                sim.getSimulationIterator().step(100);
            } catch (Exception e) {
                sim.println(e.toString());
            }
        }

    }

    private void setMesh(double scalingFactorX, double scalingFactorY) {

        Simulation sim
                = getActiveSimulation();

        CadModel cadModel_0
                = ((CadModel) sim.get(SolidModelManager.class).getObject("3D-CAD Model 1"));

        cadModel_0.update();

        SolidModelPart solidModelPart_0
                = ((SolidModelPart) sim.get(SimulationPartManager.class).getPart("Body 1"));

        sim.get(SimulationPartManager.class).updateParts(new NeoObjectVector(new Object[]{solidModelPart_0}));

        DirectedMeshOperation dmOp
                = ((DirectedMeshOperation) sim.get(MeshOperationManager.class).getObject("Directed Mesh"));

        DirectedPatchSourceMesh dmSourceMesh
                = ((DirectedPatchSourceMesh) dmOp.getGuidedSurfaceMeshBaseManager().getObject("Patch Mesh"));

        dmOp.getGuidedSurfaceMeshBaseManager().remove(dmSourceMesh);

        // Run Badge to 2D
        sim.get(MeshOperationManager.class).getObject("Badge for 2D Meshing").execute();

        dmOp.editDirectedMeshOperation();

        PartSurface partSurface_0
                = ((PartSurface) solidModelPart_0.getPartSurfaceManager().getPartSurface("fluid"));

        DirectedMeshPartCollection directedMeshPartCollection_0
                = ((DirectedMeshPartCollection) dmOp.getGuidedMeshPartCollectionManager().getObject("Body 1"));

        dmOp.getGuidedSurfaceMeshBaseManager().createPatchSourceMesh(new NeoObjectVector(new Object[]{partSurface_0}), directedMeshPartCollection_0);

        dmSourceMesh = ((DirectedPatchSourceMesh) dmOp.getGuidedSurfaceMeshBaseManager().getObject("Patch Mesh"));

        dmSourceMesh.editDirectedPatchSourceMesh();

        dmSourceMesh.autopopulateFeatureEdges();

        dmSourceMesh.enablePatchMeshMode();

        ScalarQuantityDesignParameter paramTubeRadius = ((ScalarQuantityDesignParameter) cadModel_0.getDesignParameterManager().getObject("TubeRadius"));
        ScalarQuantityDesignParameter paramTubeLength = ((ScalarQuantityDesignParameter) cadModel_0.getDesignParameterManager().getObject("TubeLength"));

        int radMeshCount = Math.max((int) Math.ceil(paramTubeRadius.getEvaluatedSIValue() * 4000 * scalingFactorY), 25) + 10;
        int axiMeshCount = (int) Math.round(2000.0 * scalingFactorX) + 50;

      for (PatchCurve crv : dmSourceMesh.getPatchCurveManager().getObjects()) {

            if (Double.compare(crv.getCurveLength(), paramTubeLength.getEvaluatedSIValue()) == 0) {

                sim.println("Adding " + crv.getPresentationName() + " to length crvs.");
                crv.getStretchingFunction().setSelected(StretchingFunctionBase.Type.TWO_SIDED_HYPERBOLIC);
                dmSourceMesh.defineMeshPatchCurve(crv, crv.getStretchingFunction(), 1.0E-3, 1.0E-3, axiMeshCount, false, false);
                sim.println("Meshing length with " + axiMeshCount + " elements.");
            } else {
                sim.println("Adding " + crv.getPresentationName() + " to radius crvs.");
                crv.getStretchingFunction().setSelected(StretchingFunctionBase.Type.TWO_SIDED_HYPERBOLIC);
                dmSourceMesh.defineMeshPatchCurve(crv, crv.getStretchingFunction(), 4.0E-4, 4.0E-4, radMeshCount, false, false);
                sim.println("Meshing radius with " + radMeshCount + " elements.");
            }

        }

        dmSourceMesh.stopEditPatchOperation();

        dmOp.stopEditingDirectedMeshOperation();

        try {
            dmOp.execute();
        } catch (Exception e) {
            sim.println(e.toString());
        }
        
        
     
    }

    private void setFirstOrderConvection() {

        Simulation sim
                = getActiveSimulation();

        PhysicsContinuum physicsContinuum_0
                = ((PhysicsContinuum) sim.getContinuumManager().getContinuum("Physics 1"));

        SegregatedFlowModel segregatedFlowModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedFlowModel.class);

        segregatedFlowModel_0.getUpwindOption().setSelected(FlowUpwindOption.Type.FIRST_ORDER);

        SegregatedFluidTemperatureModel segregatedFluidTemperatureModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedFluidTemperatureModel.class);

        segregatedFluidTemperatureModel_0.getConvectiveFluxOption().setSelected(ConvectiveFluxOption.Type.FIRST_ORDER);

        SegregatedSpeciesModel segregatedSpeciesModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedSpeciesModel.class);

        segregatedSpeciesModel_0.getConvectiveFluxOption().setSelected(ConvectiveFluxOption.Type.FIRST_ORDER);
    }

    private void setSecondOrderConvection() {

        Simulation sim
                = getActiveSimulation();
        PhysicsContinuum physicsContinuum_0
                = ((PhysicsContinuum) sim.getContinuumManager().getContinuum("Physics 1"));

        SegregatedFlowModel segregatedFlowModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedFlowModel.class);

        segregatedFlowModel_0.getUpwindOption().setSelected(FlowUpwindOption.Type.SECOND_ORDER);

        SegregatedFluidTemperatureModel segregatedFluidTemperatureModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedFluidTemperatureModel.class);

        segregatedFluidTemperatureModel_0.getConvectiveFluxOption().setSelected(ConvectiveFluxOption.Type.SECOND_ORDER);

        SegregatedSpeciesModel segregatedSpeciesModel_0
                = physicsContinuum_0.getModelManager().getModel(SegregatedSpeciesModel.class);

        segregatedSpeciesModel_0.getConvectiveFluxOption().setSelected(ConvectiveFluxOption.Type.SECOND_ORDER);
    }

    private void setDefaultAnnotations() {

        Simulation sim = getActiveSimulation();

        SimpleAnnotation labelTemp = ((SimpleAnnotation) sim.getAnnotationManager().getObject("Label_WallTemp"));
        SimpleAnnotation labelRad = ((SimpleAnnotation) sim.getAnnotationManager().getObject("Label_Rad"));

        ScalarGlobalParameter temperature = ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("WallTemperature"));
        ScalarGlobalParameter radius = ((ScalarGlobalParameter) sim.get(GlobalParameterManager.class).getObject("tubeRadius"));

        labelTemp.setText("Wall temperature: " + temperature.getQuantity().getSIValue() + " K");
        labelRad.setText("Tube radius: " + radius.getQuantity().getSIValue() + " m");

    }

    private void setPostAnnotations() {

        Simulation sim = getActiveSimulation();

        SimpleAnnotation labelYield = ((SimpleAnnotation) sim.getAnnotationManager().getObject("Label_Yield"));

        double yield = 0.0;
        labelYield.setText("YieldCO: " + yield);
    }

}
