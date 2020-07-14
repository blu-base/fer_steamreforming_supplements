// STAR-CCM+ macro
// Written by Sebastian Engel 01/2019
package tbr;

import java.util.*;

import star.cadmodeler.*;
import star.common.*;
import star.base.neo.*;
import star.vis.*;
import star.prismmesher.*;
import star.meshing.*;
import star.twodmesher.*;
import star.species.*;
import star.turbulence.*;
import star.coupledflow.*;
import star.energy.*;
import star.flow.*;
import star.keturb.*;
import star.material.*;
import star.radiation.common.*;
import star.radiation.dom.*;
import star.base.report.*;

// Reading CSV
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import star.metrics.CellQualityRemediationModel;

import star.resurfacer.SurfaceGrowthRate;

public class reactor extends StarMacro {

  // -----------------------------------------------------------------------------------
  // Derived Data
  // -----------------------------------------------------------------------------------  
  // 2D Tube Bundle Ractor
  //   WIDTH
  // H┌────────────────────┐
  // E│   .   .    .   .   │
  // I│     .   .    .     │
  // G│   .   .    .   .   │
  // H│     .   .    .     │
  // T│   .   .    .   .   │
  //  │   ┌─────><─────┐   │
  //  │   │            │   │
  //  │In │            │Out│
  //
  //  >< Coordinate Center (centered on south wall)
  // Geometric Input Parameters
  // WIDTH direction is Y-axis
  // HEIGHT direction is X-axis
  private static final double BURNER_TOTAL_WIDTH = 2.1;    // m
  private static final double BURNER_TOTAL_HEIGHT = 3.1;    // m

  private static final double INLET_WIDTH = 0.2;  // m
  private static final double INLET_LENGTH = 0.5; //m

  private static final double OUTLET_WIDTH = INLET_WIDTH;
  private static final double OUTLET_LENGTH = INLET_LENGTH;

  private static final double PIPE_RADIUS = 0.073;

  // Other Input Parameters
  private static final String SIM_FILENAME = "reactor";
  private static final String CSVINPUT_FILENAME = "pipes.csv";
  private static final String MASSFLOWINPUT = "massflow.input";
  private static final String BASESIZEINPUT = "basesize.input";

  private final double[] inletComponentfractionsVector = new double[]{0.05, 0.1, 0.1, 0.75};

  // Simulation Parameters
  private static final double PIPE_SAFEDISTANCE = 0.4 * PIPE_RADIUS;
  private static final int SIM_MINSTEPS = 2000;
  private static final int SIM_MAXSTEPS_ALLOWED = 10000;
  // private final double MESH_BASESIZE = 0.005;

  // -----------------------------------------------------------------------------------
  // Derived Data
  // -----------------------------------------------------------------------------------
  // Main Volumes Corner points.
  private final double[] mainCornerPoint_SW = {0.0, -BURNER_TOTAL_WIDTH / 2};
  private final double[] mainCornerPoint_SE = {0.0, BURNER_TOTAL_WIDTH / 2};
  private final double[] mainCornerPoint_NW = {BURNER_TOTAL_HEIGHT, -BURNER_TOTAL_WIDTH / 2};
  private final double[] mainCornerPoint_NE = {BURNER_TOTAL_HEIGHT, BURNER_TOTAL_WIDTH / 2};

  //  Inlet Channel corner points
  private final double[] inletCornerPoints_NW = {0.0, BURNER_TOTAL_WIDTH / 2};
  private final double[] inletCornerPoints_NE = {0.0, BURNER_TOTAL_WIDTH / 2 - INLET_WIDTH};
  private final double[] inletCornerPoints_SW = {-INLET_LENGTH, BURNER_TOTAL_WIDTH / 2};
  private final double[] inletCornerPoints_SE = {-INLET_LENGTH, BURNER_TOTAL_WIDTH / 2 - INLET_WIDTH};
  // Outlet Channel corner points
  private final double[] outletCornerPoints_NW = {0.0, -BURNER_TOTAL_WIDTH / 2};
  private final double[] outletCornerPoints_NE = {0.0, -BURNER_TOTAL_WIDTH / 2 + OUTLET_WIDTH};
  private final double[] outletCornerPoints_SW = {-OUTLET_LENGTH, -BURNER_TOTAL_WIDTH / 2};
  private final double[] outletCornerPoints_SE = {-OUTLET_LENGTH, -BURNER_TOTAL_WIDTH / 2 + OUTLET_WIDTH};

  // Fields
  private List<double[]> pipecenters = new ArrayList<>();
  private Simulation sim;
  private CadModel param_cadModel;

  /**
   * Run
   */
  @Override
  public void execute() {

    // build geometry in StarCCM CAD
    setCADpart();

    // Generate Mesh from CAD part
    set2Dmesh();

    setPhysics();

    setBC();

    setReports();

    setScenes();

    setSolverSettings();

    setStoppingCriteria();

    // Running the Simulation
    runSim();
    saveAll();
  }

  private void setCADpart() {

    msg("Beginning CAD creation.");
    sim = getActiveSimulation();

    pipecenters = read_pipe_coordinates(CSVINPUT_FILENAME);

    Scene scene_1 = sim.getSceneManager().createScene("Param_Geo");
    scene_1.initializeAndWait();

    param_cadModel = sim.get(SolidModelManager.class).createSolidModel(scene_1);

    sim.println("Building the base geometry");
    cadModel_Base_Inlet(sim);
    cadModel_Base_Outlet(sim);
    cadModel_Base(sim);

    sim.println("Uniting the base geometry");
    UniteBodiesFeature uniteBodiesFeature_Base = param_cadModel.getFeatureManager().createUniteBodies();

    star.cadmodeler.Body cadmodelerBody_Outlet = ((star.cadmodeler.Body) param_cadModel.getBody("Base_Outlet"));
    star.cadmodeler.Body cadmodelerBody_Inlet = ((star.cadmodeler.Body) param_cadModel.getBody("Base_Inlet"));
    star.cadmodeler.Body cadmodelerBody_Base = ((star.cadmodeler.Body) param_cadModel.getBody("Base"));

    uniteBodiesFeature_Base.setBodies(new NeoObjectVector(new Object[]{cadmodelerBody_Base, cadmodelerBody_Inlet, cadmodelerBody_Outlet}));
    uniteBodiesFeature_Base.setImprintOption(0);
    uniteBodiesFeature_Base.setTransferFaceNames(true);
    uniteBodiesFeature_Base.setTransferBodyNames(false);

    param_cadModel.getFeatureManager().execute(uniteBodiesFeature_Base);

    sim.println("Building the pipe geometries");
    cadModel_Pipes(pipecenters, sim, param_cadModel);

    // Check whether there are any problems with the pipe geometries from coords
    arePipesIntersectingEachOther();
    arePipesIntersectingWalls();
    arePipesInsideDomain();

    cadModel_finalMerge(sim, param_cadModel);

  }

  private void cadModel_Base_Inlet(Simulation sim) {
    msg("Building inlet geometry.");

    // Inlet sketch
    Sketch inlet_sketch = create_sketch_xy("Sketch_Inlet", sim, param_cadModel);

    param_cadModel.getFeatureManager().startSketchEdit(inlet_sketch);

    PointSketchPrimitive inlet_SketchPoint1 = ((PointSketchPrimitive) inlet_sketch.createPoint(new DoubleVector(inletCornerPoints_NE)));
    PointSketchPrimitive inlet_SketchPoint2 = ((PointSketchPrimitive) inlet_sketch.createPoint(new DoubleVector(inletCornerPoints_SE)));
    PointSketchPrimitive inlet_SketchPoint3 = ((PointSketchPrimitive) inlet_sketch.createPoint(new DoubleVector(inletCornerPoints_SW)));
    PointSketchPrimitive inlet_SketchPoint4 = ((PointSketchPrimitive) inlet_sketch.createPoint(new DoubleVector(inletCornerPoints_NW)));

    LineSketchPrimitive inlet_SketchLine1 = inlet_sketch.createLine(inlet_SketchPoint1, inlet_SketchPoint2);
    LineSketchPrimitive inlet_SketchLine2 = inlet_sketch.createLine(inlet_SketchPoint2, inlet_SketchPoint3);
    LineSketchPrimitive inlet_SketchLine3 = inlet_sketch.createLine(inlet_SketchPoint3, inlet_SketchPoint4);
    LineSketchPrimitive inlet_SketchLine4 = inlet_sketch.createLine(inlet_SketchPoint4, inlet_SketchPoint1);

    inlet_sketch.markFeatureForEdit();
    param_cadModel.getFeatureManager().stopSketchEdit(inlet_sketch, true);
    inlet_sketch.setIsUptoDate(true);
    param_cadModel.getFeatureManager().rollForwardToEnd();

    // Extruding Inlet    
    ExtrusionMerge extrusionMerge_inlet = extrude_woMerge(inlet_sketch, sim, param_cadModel);

    // Naming the inlets faces
    Face face_inlet_inlet = ((Face) extrusionMerge_inlet.getSideFace(inlet_SketchLine2, "True"));
    Face face_inlet_wall = ((Face) extrusionMerge_inlet.getSideFace(inlet_SketchLine1, "True"));
    Face face_inlet_sym = ((Face) extrusionMerge_inlet.getSideFace(inlet_SketchLine3, "True"));

    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_inlet_inlet}), "Inlet");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_inlet_wall}), "WALL");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_inlet_sym}), "WALL");

    for (Face face : extrusionMerge_inlet.getStartCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID");
    }
    for (Face face : extrusionMerge_inlet.getEndCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID_sh");
    }

    star.cadmodeler.Body cadmodelerBody_inlet = ((star.cadmodeler.Body) extrusionMerge_inlet.getBody(inlet_SketchLine1));
    cadmodelerBody_inlet.setPresentationName("Base_Inlet");

  }

  private void cadModel_Base_Outlet(Simulation sim) {
    msg("Building outlet geometry.");

    // Inlet sketch
    Sketch outlet_sketch = create_sketch_xy("Sketch_Outlet", sim, param_cadModel);

    param_cadModel.getFeatureManager().startSketchEdit(outlet_sketch);

    PointSketchPrimitive outlet_SketchPoint1 = ((PointSketchPrimitive) outlet_sketch.createPoint(new DoubleVector(outletCornerPoints_NE)));
    PointSketchPrimitive outlet_SketchPoint2 = ((PointSketchPrimitive) outlet_sketch.createPoint(new DoubleVector(outletCornerPoints_SE)));
    PointSketchPrimitive outlet_SketchPoint3 = ((PointSketchPrimitive) outlet_sketch.createPoint(new DoubleVector(outletCornerPoints_SW)));
    PointSketchPrimitive outlet_SketchPoint4 = ((PointSketchPrimitive) outlet_sketch.createPoint(new DoubleVector(outletCornerPoints_NW)));

    LineSketchPrimitive outlet_SketchLine1 = outlet_sketch.createLine(outlet_SketchPoint1, outlet_SketchPoint2);
    LineSketchPrimitive outlet_SketchLine2 = outlet_sketch.createLine(outlet_SketchPoint2, outlet_SketchPoint3);
    LineSketchPrimitive outlet_SketchLine3 = outlet_sketch.createLine(outlet_SketchPoint3, outlet_SketchPoint4);
    LineSketchPrimitive outlet_SketchLine4 = outlet_sketch.createLine(outlet_SketchPoint4, outlet_SketchPoint1);

    outlet_sketch.markFeatureForEdit();
    param_cadModel.getFeatureManager().stopSketchEdit(outlet_sketch, true);
    outlet_sketch.setIsUptoDate(true);
    param_cadModel.getFeatureManager().rollForwardToEnd();

    // Extruding Inlet
    ExtrusionMerge extrusionMerge_outlet = extrude_woMerge(outlet_sketch, sim, param_cadModel);

    // Naming the inlets faces
    Face face_outlet_outlet = ((Face) extrusionMerge_outlet.getSideFace(outlet_SketchLine2, "True"));
    Face face_outlet_sym = ((Face) extrusionMerge_outlet.getSideFace(outlet_SketchLine1, "True"));
    Face face_outlet_wall = ((Face) extrusionMerge_outlet.getSideFace(outlet_SketchLine3, "True"));

    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_outlet_outlet}), "Outlet");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_outlet_sym}), "WALL");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_outlet_wall}), "WALL");

    for (Face face : extrusionMerge_outlet.getStartCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID");
    }
    for (Face face : extrusionMerge_outlet.getEndCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID_sh");
    }

    star.cadmodeler.Body cadmodelerBody_inlet = ((star.cadmodeler.Body) extrusionMerge_outlet.getBody(outlet_SketchLine1));
    cadmodelerBody_inlet.setPresentationName("Base_Outlet");

  }

  // Used to create main body Volume of the Heat exchanger
  private void cadModel_Base(Simulation sim) {
    msg("Building Main geometry.");

    // Start sketch
    Sketch base_sketch = create_sketch_xy("Sketch_Base", sim, param_cadModel);

    param_cadModel.getFeatureManager().startSketchEdit(base_sketch);

    PointSketchPrimitive base_SketchPoint1 = ((PointSketchPrimitive) base_sketch.createPoint(new DoubleVector(mainCornerPoint_SW)));
    PointSketchPrimitive base_SketchPoint2 = ((PointSketchPrimitive) base_sketch.createPoint(new DoubleVector(mainCornerPoint_SE)));
    PointSketchPrimitive base_SketchPoint3 = ((PointSketchPrimitive) base_sketch.createPoint(new DoubleVector(mainCornerPoint_NE)));
    PointSketchPrimitive base_SketchPoint4 = ((PointSketchPrimitive) base_sketch.createPoint(new DoubleVector(mainCornerPoint_NW)));

    LineSketchPrimitive base_SketchLine1 = base_sketch.createLine(base_SketchPoint1, base_SketchPoint2);
    LineSketchPrimitive base_SketchLine2 = base_sketch.createLine(base_SketchPoint2, base_SketchPoint3);
    LineSketchPrimitive base_SketchLine3 = base_sketch.createLine(base_SketchPoint3, base_SketchPoint4);
    LineSketchPrimitive base_SketchLine4 = base_sketch.createLine(base_SketchPoint4, base_SketchPoint1);

    base_sketch.markFeatureForEdit();
    param_cadModel.getFeatureManager().stopSketchEdit(base_sketch, true);
    base_sketch.setIsUptoDate(true);
    param_cadModel.getFeatureManager().rollForwardToEnd();

    // Extruding Base
    ExtrusionMerge extrusionMerge_base = extrude_woMerge(base_sketch, sim, param_cadModel);

    // Naming the Base faces  
    Face face_base_base = ((Face) extrusionMerge_base.getSideFace(base_SketchLine1, "True"));
    Face face_base_wall1 = ((Face) extrusionMerge_base.getSideFace(base_SketchLine2, "True"));
    Face face_base_wall2 = ((Face) extrusionMerge_base.getSideFace(base_SketchLine3, "True"));
    Face face_base_wall3 = ((Face) extrusionMerge_base.getSideFace(base_SketchLine4, "True"));

    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_base_wall1}), "WALL");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_base_wall2}), "WALL");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_base_wall3}), "WALL");
    param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_base_base}), "WALL");

    for (Face face : extrusionMerge_base.getStartCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID");
    }
    for (Face face : extrusionMerge_base.getEndCapFaces()) {
      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID_sh");
    }

    star.cadmodeler.Body cadmodelerBody_inlet = ((star.cadmodeler.Body) extrusionMerge_base.getBody(base_SketchLine1));
    cadmodelerBody_inlet.setPresentationName("Base");

  }

  // creating pipe volumes without merging them with other geometry.
  private void cadModel_Pipes(List<double[]> pipe_centers, Simulation sim, CadModel param_cadModel) {
    msg("Creating Pipes.");

    FeatureManager param_Features = param_cadModel.getFeatureManager();

    Iterator oc_pipes = pipe_centers.iterator();
    int counter = 1;

    while (oc_pipes.hasNext()) {

      double[] coords = (double[]) oc_pipes.next();

      // Creating a new sketch
      Sketch sketch = create_sketch_xy("Pipe_" + counter, sim, param_cadModel);

      param_Features.startSketchEdit(sketch);

      CircleSketchPrimitive circleSketchPrimitive_0 = sketch.createCircle(new DoubleVector(coords), PIPE_RADIUS);

      sketch.markFeatureForEdit();
      param_Features.stopSketchEdit(sketch, true);
      sketch.setIsUptoDate(true);
      param_Features.rollForwardToEnd();

      // Extruding the sketch
      ExtrusionMerge em = extrude_woMerge(sketch, sim, param_cadModel);

      // name Body  
      star.cadmodeler.Body cadmodelerBody_0 = ((star.cadmodeler.Body) em.getBody(circleSketchPrimitive_0));

      cadmodelerBody_0.setPresentationName("Pipe_" + counter);

      // name BC          
      Face face_0 = ((Face) em.getSideFace(circleSketchPrimitive_0, "True"));

      param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face_0}), "PWALL_" + counter);

      for (Face face : em.getStartCapFaces()) {
        param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID");
      }
      for (Face face : em.getEndCapFaces()) {
        param_cadModel.setFaceNameAttributes(new NeoObjectVector(new Object[]{face}), "FLUID_sh");
      }

      counter++;
    }

    counter--;
    msg("Created " + counter + " Pipes.");
  }

  private void cadModel_finalMerge(Simulation sim, CadModel param_cadModel) {
    // Setting target and tool body collections
    Collection<Body> targetbodies = new ArrayList<>();
    Collection<Body> toolbodies = new ArrayList<>();

    targetbodies.add(((star.cadmodeler.Body) param_cadModel.getBody("Base")));

    for (int i = 1; i < pipecenters.size() + 1; i++) {
      toolbodies.add((star.cadmodeler.Body) param_cadModel.getBody("Pipe_" + i));
    }

    // Substraction Feature
    SubtractBodiesFeature subtractBodiesFeature
            = param_cadModel.getFeatureManager().createSubtractBodies();

    BodyNameRefManager bodyNameRefManager_target
            = subtractBodiesFeature.getTargetBodyGroup();
    bodyNameRefManager_target.setBodies(targetbodies);

    BodyNameRefManager bodyNameRefManager_tools
            = subtractBodiesFeature.getToolBodyGroup();
    bodyNameRefManager_tools.setBodies(toolbodies);

    // options
    subtractBodiesFeature.setKeepToolBodies(false);
    subtractBodiesFeature.setImprint(false);
    subtractBodiesFeature.setImprintOption(0);
    subtractBodiesFeature.setTransferFaceNames(true);
    subtractBodiesFeature.setTransferBodyNames(false);
    subtractBodiesFeature.markFeatureForEdit();

    // executing
    param_cadModel.getFeatureManager().execute(subtractBodiesFeature);
  }

  private void set2Dmesh() {


    msg("Starting to Mesh.");

    Units units_m = sim.getUnitsManager().getPreferredUnits(new IntVector(new int[]{0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));

    sim = getActiveSimulation();

    if (param_cadModel.isEmpty()) {
      param_cadModel = ((CadModel) sim.get(SolidModelManager.class).getObject("3D-CAD Model 1"));
    }
    //   convert CAD to Solid Model
    star.cadmodeler.Body baseBody = ((star.cadmodeler.Body) param_cadModel.getBody("Base"));
    param_cadModel.createParts(new NeoObjectVector(new Object[]{baseBody}), "SharpEdges", 30.0, 4, true, 1.0E-5);

    SolidModelPart baseSolid = ((SolidModelPart) sim.get(SimulationPartManager.class).getPart("Base"));

    // 2d badge
    PrepareFor2dOperation prepareFor2dOperation_2
            = (PrepareFor2dOperation) sim.get(MeshOperationManager.class).createPrepareFor2dOperation(new NeoObjectVector(new Object[]{baseSolid}));

    prepareFor2dOperation_2.execute();

    // Creating region
    sim.getRegionManager().newRegionsFromParts(new NeoObjectVector(new Object[]{baseSolid}), "OneRegionPerPart", null, "OneBoundaryPerPartSurface", null, "OneFeatureCurve", null, RegionManager.CreateInterfaceMode.BOUNDARY);

    // Mesher settings
    AutoMeshOperation2d meshOp = sim.get(MeshOperationManager.class).createAutoMeshOperation2d(new StringVector(new String[]{"star.twodmesher.DualAutoMesher2d", "star.prismmesher.PrismAutoMesher"}), new NeoObjectVector(new Object[]{baseSolid}));
    AutoMeshDefaultValuesManager meshOpDefaults = meshOp.getDefaultValues();

    DualAutoMesher2d dualMesher = ((DualAutoMesher2d) meshOp.getMeshers().getObject("Polygonal Mesher"));
    dualMesher.setDoCompatibilityRefinement(true);
    dualMesher.setMinimumFaceQuality(0.2);

    double meshBaseSize = readDoubleFromFile(BASESIZEINPUT);
    meshOp.getDefaultValues().get(BaseSize.class).setValue(meshBaseSize);

    NumPrismLayers numPrismLayers_0 = meshOpDefaults.get(NumPrismLayers.class);
    numPrismLayers_0.setNumLayers(35);

    PrismLayerStretching prismLayerStretching_0 = meshOpDefaults.get(PrismLayerStretching.class);
    prismLayerStretching_0.setStretching(1.2);

    PrismThickness prismThickness_0 = meshOpDefaults.get(PrismThickness.class);
    prismThickness_0.setAbsoluteSize(0.04, units_m);

    SurfaceCurvature surfaceCurvature_0 = meshOpDefaults.get(SurfaceCurvature.class);
    surfaceCurvature_0.setEnableCurvatureDeviationDist(true);
    surfaceCurvature_0.getCurvatureDeviationDistance().setValue(0.001);

    SurfaceGrowthRate surfaceGrowthRate_0 = meshOpDefaults.get(SurfaceGrowthRate.class);
    surfaceGrowthRate_0.setGrowthRate(1.1);

    PrismAutoMesher prismAutoMesher_0 = ((PrismAutoMesher) meshOp.getMeshers().getObject("Prism Layer Mesher"));
    prismAutoMesher_0.setConvexAngleLimit(230.0);

    // Preset boundaries for correct meshing
    Region reg = sim.getRegionManager().getRegion("Base");

    Boundary boundary_0 = reg.getBoundaryManager().getBoundary("Inlet");
    MassFlowBoundary massFlowBoundary_0 = ((MassFlowBoundary) sim.get(ConditionTypeManager.class).get(MassFlowBoundary.class));
    boundary_0.setBoundaryType(massFlowBoundary_0);

//    Boundary boundary_1 = reg.getBoundaryManager().getBoundary("SYM");
//    SymmetryBoundary symmetryBoundary_0 = ((SymmetryBoundary) sim.get(ConditionTypeManager.class).get(SymmetryBoundary.class));
//    boundary_1.setBoundaryType(symmetryBoundary_0);
    Boundary boundary_2 = reg.getBoundaryManager().getBoundary("Outlet");
    PressureBoundary pressureBoundary_0 = ((PressureBoundary) sim.get(ConditionTypeManager.class).get(PressureBoundary.class));
    boundary_2.setBoundaryType(pressureBoundary_0);

    // Mesh now
    meshOp.execute();

    msg("Meshing done.");

    // Generating Report
    FvRepresentation fvRepresentation_0 = ((FvRepresentation) sim.getRepresentationManager().getObject("Volume Mesh"));
    fvRepresentation_0.generateMeshReport(new NeoObjectVector(new Object[]{reg}));

  }

  private void setPhysics() {
    sim = getActiveSimulation();

    msg("Setting up Physics Continuum");

    //Create physics      
    PhysicsContinuum phyConti = ((PhysicsContinuum) sim.getContinuumManager().getContinuum("Physics 1"));

    // Enabling physics
    phyConti.enable(SteadyModel.class);

    // MultiComponentGas
    phyConti.enable(MultiComponentGasModel.class);
    phyConti.enable(NonReactingModel.class);

    // Coupled Flow
    phyConti.enable(CoupledFlowModel.class);
    phyConti.enable(CoupledSpeciesModel.class);

    // Density
    phyConti.enable(IdealGasModel.class);
    phyConti.enable(CoupledEnergyModel.class);

    // Turbulence Model
    phyConti.enable(TurbulentModel.class);
    phyConti.enable(RansTurbulenceModel.class);
    phyConti.enable(KEpsilonTurbulence.class);
    phyConti.enable(RkeTwoLayerTurbModel.class);
    phyConti.enable(KeTwoLayerAllYplusWallTreatment.class);

    // Radiation
    phyConti.enable(RadiationModel.class);
    phyConti.enable(DORadiationModel.class);
    phyConti.enable(ParticipatingGrayModel.class);

    // Cell quality remediation
    phyConti.enable(CellQualityRemediationModel.class);

    
    
    // Setting up Gas componants
    MultiComponentGasModel multiComponentGasModel_0 = phyConti.getModelManager().getModel(MultiComponentGasModel.class);
    GasMixture gasMixture_0 = ((GasMixture) multiComponentGasModel_0.getMixture());

    gasMixture_0.getMaterialProperties().getMaterialProperty(DynamicViscosityProperty.class).setMethod(MoleFractionMixingDynamicViscosityMethod.class);

    star.material.MaterialDataBase materialMaterialDataBase_0 = sim.get(MaterialDataBaseManager.class).getMaterialDataBase("props");
    star.material.DataBaseMaterialManager materialDataBaseMaterialManager_0 = materialMaterialDataBase_0.getFolder("Gases");

    star.material.DataBaseGas matCO2 = ((star.material.DataBaseGas) materialDataBaseMaterialManager_0.getMaterial("CO2_Gas"));
    star.material.DataBaseGas matH2O = ((star.material.DataBaseGas) materialDataBaseMaterialManager_0.getMaterial("H2O_Gas"));
    star.material.DataBaseGas matN2 = ((star.material.DataBaseGas) materialDataBaseMaterialManager_0.getMaterial("N2_Gas"));
    star.material.DataBaseGas matO2 = ((star.material.DataBaseGas) materialDataBaseMaterialManager_0.getMaterial("O2_Gas"));

    Object[] componentList = new Object[]{matCO2, matH2O, matO2, matN2};
    gasMixture_0.getComponents().addComponents(new NeoObjectVector(componentList));

    // Setting Temperature dependent Viscosity and Specific Heat for Components
    Collection<MixtureComponent> components = gasMixture_0.getComponents().getComponents();

    for (MixtureComponent comp : components) {
      MaterialPropertyManager compManager = comp.getMaterialProperties();

      compManager.getMaterialProperty(DynamicViscosityProperty.class)
              .setMethod(SutherlandLaw.class);
      compManager.getMaterialProperty(SpecificHeatProperty.class)
              .setMethod(GasKineticsSpecificHeat.class);
    }

    // Massfractions
//        MassFractionProfile massFractionProfile_0 = phyConti.getInitialConditions().get(MassFractionProfile.class);
//        massFractionProfile_0.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(componentfractionsVector));
//               
    // MoleFractions
    phyConti.getInitialConditions().get(SpeciesSpecificationOption.class).setSelected(SpeciesSpecificationOption.Type.MOLE_FRACTION);
    MoleFractionProfile moleFractionProfile_0 = phyConti.getInitialConditions().get(MoleFractionProfile.class);
    moleFractionProfile_0.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(inletComponentfractionsVector));

    ParticipatingGrayModel participatingGrayModel_0 = phyConti.getModelManager().getModel(ParticipatingGrayModel.class);
    RadiationTemperature radiationTemperature_0 = participatingGrayModel_0.getThermalEnvironmentManager().get(RadiationTemperature.class);
    radiationTemperature_0.getEnvRadTemp().setValue(1100);

    // Set Temperature Limits and Initial condition
    phyConti.getReferenceValues().get(MaximumAllowableTemperature.class).setValue(1900.0);
    phyConti.getReferenceValues().get(MinimumAllowableTemperature.class).setValue(300.0);

    StaticTemperatureProfile staticTemperatureProfile_0 = phyConti.getInitialConditions().get(StaticTemperatureProfile.class);
    staticTemperatureProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(1100.0);

    // Turbulence initial condition
    TurbulenceIntensityProfile turbulenceIntensityProfile_0 = phyConti.getInitialConditions().get(TurbulenceIntensityProfile.class);
    turbulenceIntensityProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(0.1);

    // Velocity Initial Condition
    VelocityProfile velocityProfile_0 = phyConti.getInitialConditions().get(VelocityProfile.class);
    velocityProfile_0.getMethod(ConstantVectorProfileMethod.class).getQuantity().setComponents(0.1, 0.0, 0.0);

  }

  private void setBC() {

    sim = getActiveSimulation();

    msg("Setting up Boundary Conditions");

    Region reg = sim.getRegionManager().getRegions().iterator().next();

    Collection<Boundary> boundaries = reg.getBoundaryManager().getBoundaries();

    // Inlet Boundary Conditions
    Boundary bdry_inlet = reg.getBoundaryManager().getBoundary("Inlet");

    RadiationTemperatureProfile radiationTemperatureProfile_0 = bdry_inlet.getValues().get(RadiationTemperatureProfile.class);
    radiationTemperatureProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(1800.0);

    // Massflow Inlet
    double massFlowRatio = readDoubleFromFile(MASSFLOWINPUT);
    int nPipes = pipecenters.size();
    double massflow = massFlowRatio * nPipes;
    msg("Setting total mass flow to: " + massflow);

    MassFlowBoundary massFlowBoundary_0 = ((MassFlowBoundary) sim.get(ConditionTypeManager.class).get(MassFlowBoundary.class));
    bdry_inlet.setBoundaryType(massFlowBoundary_0);

    MassFlowRateProfile massFlowRateProfile_0 = bdry_inlet.getValues().get(MassFlowRateProfile.class);
    massFlowRateProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(massflow);

//    // Velocity Inlet
//    InletBoundary inletBoundary_0 = ((InletBoundary) sim.get(ConditionTypeManager.class).get(InletBoundary.class));
//    bdry_inlet.setBoundaryType(inletBoundary_0);
//
//    VelocityMagnitudeProfile velocityMagnitudeProfile_0 = bdry_inlet.getValues().get(VelocityMagnitudeProfile.class);
//    velocityMagnitudeProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity()
//            .setValue(5.0);
//        MassFractionProfile massFractionProfile_inlet = bdry_inlet.getValues().get(MassFractionProfile.class);       
//        massFractionProfile_inlet.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(componentfractionsVector));
//        
    bdry_inlet.getConditions().get(SpeciesSpecificationOption.class).setSelected(SpeciesSpecificationOption.Type.MOLE_FRACTION);
    MoleFractionProfile moleFractionProfile_inlet = bdry_inlet.getValues().get(MoleFractionProfile.class);
    moleFractionProfile_inlet.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(inletComponentfractionsVector));

    TotalTemperatureProfile totalTemperatureProfile_0 = bdry_inlet.getValues().get(TotalTemperatureProfile.class);
    totalTemperatureProfile_0.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(1800.0);

    // Outlet Boundary Conditions
    Boundary bdry_outlet = reg.getBoundaryManager().getBoundary("Outlet");
    RadiationTemperatureProfile radiationTemperatureProfile_1 = bdry_outlet.getValues().get(RadiationTemperatureProfile.class);
    radiationTemperatureProfile_1.getMethod(ConstantScalarProfileMethod.class).getQuantity()
            .setValue(1100.0);

//        MassFractionProfile massFractionProfile_outlet = bdry_inlet.getValues().get(MassFractionProfile.class);       
//        massFractionProfile_outlet.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(componentfractionsVector));
//           
    bdry_outlet.getConditions().get(SpeciesSpecificationOption.class).setSelected(SpeciesSpecificationOption.Type.MOLE_FRACTION);
    MoleFractionProfile moleFractionProfile_outlet = bdry_outlet.getValues().get(MoleFractionProfile.class);
    moleFractionProfile_outlet.getMethod(ConstantArrayProfileMethod.class).getQuantity().setArray(new DoubleVector(inletComponentfractionsVector));

    StaticTemperatureProfile staticTemperatureProfile_1 = bdry_outlet.getValues().get(StaticTemperatureProfile.class);
    staticTemperatureProfile_1.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(1100.0);    
    
    // Load Table with Wall Heat Flux data
    sim.getTableManager().createFromFile(resolvePath("Q_of_T.csv"));

    UserFieldFunction uFF_wallFlux = sim.getFieldFunctionManager().createFieldFunction();
    uFF_wallFlux.getTypeOption().setSelected(FieldFunctionTypeOption.Type.SCALAR);
    uFF_wallFlux.setDefinition("interpolateTable(@Table(\"Q_of_T\"), \"Temperature\", LINEAR, \"Heat Flux\",${Temperature})");
    uFF_wallFlux.setPresentationName("PipeHeatFlux");
    uFF_wallFlux.setFunctionName("pipeHeatFlux");

    // Setting the Pipe Wall Boundary Conditions
    for (Boundary bndry : boundaries) {
      if (bndry.getBoundaryType() instanceof WallBoundary) {
        if (bndry.getPresentationName().matches("^PWALL_.*")) {
          bndry.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.HEAT_FLUX);
          HeatFluxProfile heatFluxProfile_0 = bndry.getValues().get(HeatFluxProfile.class);
          heatFluxProfile_0.setMethod(FunctionScalarProfileMethod.class);
          heatFluxProfile_0.getMethod(FunctionScalarProfileMethod.class).setFieldFunction(uFF_wallFlux);

        }
      }
    }
  }

  private void setReports() {
    sim = getActiveSimulation();

    msg("Setting up Reports");

    PrimitiveFieldFunction ff_Temperature = ((PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Temperature"));
    PrimitiveFieldFunction ff_Enthalpy = ((PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("TotalEnthalpy"));
    PrimitiveFieldFunction ffv_area = ((PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Area"));
    PrimitiveFieldFunction ff_heatflux_Radiation = ((PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("BoundaryRadiationHeatFlux"));
    PrimitiveFieldFunction ff_heatflux_Conduction = ((PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("BoundaryConductionHeatFlux"));

    VectorMagnitudeFieldFunction ff_area = ((VectorMagnitudeFieldFunction) ffv_area.getMagnitudeFunction());

    Region reg = sim.getRegionManager().getRegion("Base");
    Boundary bdry_outlet = reg.getBoundaryManager().getBoundary("Outlet");
    Boundary bdry_inlet = reg.getBoundaryManager().getBoundary("Inlet");

    Collection<Boundary> boundaries = reg.getBoundaryManager().getBoundaries();
    Collection<Boundary> pipesBndryList = new ArrayList<>();
    Collection<Boundary> inoutList = new ArrayList<>();

    inoutList.add(bdry_inlet);
    inoutList.add(bdry_outlet);

    for (Boundary bndry : boundaries) {
      if (bndry.getBoundaryType() instanceof WallBoundary) {
        if (bndry.getPresentationName().matches("^PWALL_.*")) {
          pipesBndryList.add(bndry);
        }
      }
    }

    // Creating Average Temperature Reports and Collect Objects in List to create One plot for Temperature Reports
    Collection<Report> temperatureReportsList = new ArrayList<>();
    Collection<Report> forceReportsList = new ArrayList<>();
    Collection<Report> heatTransferReportsList = new ArrayList<>();
    Collection<Report> heatTransferPerAreaReportsList = new ArrayList<>();
    Collection<Report> enthTransferReportsList = new ArrayList<>();
    Collection<Report> radheatTransferReportsList = new ArrayList<>();
    Collection<Report> condheatTransferReportsList = new ArrayList<>();
    
    ReportManager simRepMngr = sim.getReportManager();
    PlotManager simPlotMngr = sim.getPlotManager();
    MonitorManager simMonMngr = sim.getMonitorManager();

    for (Boundary bndry : pipesBndryList) {
      String bndryName = bndry.getPresentationName();
      // Temperature Reports
      AreaAverageReport areaAverageReport_temp = simRepMngr.createReport(AreaAverageReport.class);
      areaAverageReport_temp.setFieldFunction(ff_Temperature);
      areaAverageReport_temp.getParts().setQuery(null);
      areaAverageReport_temp.getParts().setObjects(bndry);
      areaAverageReport_temp.setPresentationName("Temperature_" + bndryName);
      temperatureReportsList.add(areaAverageReport_temp);

      // Force Reports
      ForceReport forceRep = simRepMngr.createReport(ForceReport.class);
      forceRep.getParts().setQuery(null);
      forceRep.getParts().setObjects(bndry);

      forceRep.setPresentationName("Force_" + bndryName);
      forceReportsList.add(forceRep);

      // Heat Transfer Reports
      HeatTransferReport heatTransRep = simRepMngr.createReport(HeatTransferReport.class);
      heatTransRep.getParts().setQuery(null);
      heatTransRep.getParts().setObjects(bndry);
      heatTransRep.setPresentationName("Heat_Transfer_W_" + bndryName);
      heatTransferReportsList.add(heatTransRep);

      // Surface Area Report
      SumReport areaReport = simRepMngr.createReport(SumReport.class);
      areaReport.setFieldFunction(ff_area);
      areaReport.getParts().setQuery(null);
      areaReport.getParts().setObjects(bndry);
      areaReport.setPresentationName("Area_" + bndryName);

      // Surface Averaged Heat Transfer
      ExpressionReport averagedHeatTransRep = simRepMngr.createReport(ExpressionReport.class);
      averagedHeatTransRep.setPresentationName("Heat_Transfer_AreaAveraged_Wm2_" + bndryName);
      averagedHeatTransRep.setDefinition("${Heat_Transfer_W_" + bndryName + "Report}/${Area_" + bndryName + "Report}");

      heatTransferPerAreaReportsList.add(averagedHeatTransRep);

      // Enthalpy
      AreaAverageReport areaAverageReport_enth = simRepMngr.createReport(AreaAverageReport.class);
      areaAverageReport_enth.setFieldFunction(ff_Enthalpy);
      areaAverageReport_enth.getParts().setQuery(null);
      areaAverageReport_enth.getParts().setObjects(bndry);
      areaAverageReport_enth.setPresentationName("TotalEnthalpy_" + bndryName);
      enthTransferReportsList.add(areaAverageReport_enth);

      // Radiation Heat Flux
      SurfaceIntegralReport surfIntegral_radHeatFlux = sim.getReportManager().createReport(SurfaceIntegralReport.class);
      surfIntegral_radHeatFlux.setPresentationName("Heat_Flux_Radiation_" + bndryName);      
      surfIntegral_radHeatFlux.setFieldFunction(ff_heatflux_Radiation);
      surfIntegral_radHeatFlux.getParts().setQuery(null);
      surfIntegral_radHeatFlux.getParts().setObjects(bndry);
      radheatTransferReportsList.add(surfIntegral_radHeatFlux);

     // condutction Heat Flux
      SurfaceIntegralReport surfIntegral_condHeatFlux = sim.getReportManager().createReport(SurfaceIntegralReport.class);
      surfIntegral_condHeatFlux.setPresentationName("Heat_Flux_Conduction_" + bndryName);      
      surfIntegral_condHeatFlux.setFieldFunction(ff_heatflux_Conduction);
      surfIntegral_condHeatFlux.getParts().setQuery(null);
      surfIntegral_condHeatFlux.getParts().setObjects(bndry);      
      condheatTransferReportsList.add(surfIntegral_condHeatFlux);
    }

    simMonMngr.createMonitorAndPlot(temperatureReportsList, true, "Pipe Temperatures Plot");
    simMonMngr.createMonitorAndPlot(forceReportsList, true, "Forces on Pipes Plot");
    simMonMngr.createMonitorAndPlot(heatTransferReportsList, true, "Heat Transfer on Pipes Plot");
    simMonMngr.createMonitorAndPlot(heatTransferPerAreaReportsList, true, "Heat Transfer Per Surface Area on Pipes Plot");
    simMonMngr.createMonitorAndPlot(enthTransferReportsList, true, "Total Enthalpy on Pipes Plot");
    simMonMngr.createMonitorAndPlot(radheatTransferReportsList, true, "Heat Transfer by Radiation on Pipes Plot");
    simMonMngr.createMonitorAndPlot(condheatTransferReportsList, true, "Heat Transfer by Conduction on Pipes Plot");

    // Create Plot from above monitors
    Collection<Monitor> temperReportMonitors = new ArrayList<>();
    Collection<Monitor> heatTrReportMonitors = new ArrayList<>();
    Collection<Monitor> heatpSATrReportMonitors = new ArrayList<>();
    Collection<Monitor> forcesReportMonitors = new ArrayList<>();
    Collection<Monitor> enthalReportMonitors = new ArrayList<>();
    Collection<Monitor> radheatTransferReportMonitors = new ArrayList<>();
    Collection<Monitor> condheatTransferReportMonitors = new ArrayList<>();

    for (Boundary bndry : pipesBndryList) {
      String bndryName = bndry.getPresentationName();
      temperReportMonitors.add(simMonMngr.getMonitor("Temperature_" + bndryName + " Monitor"));
      heatTrReportMonitors.add(simMonMngr.getMonitor("Heat_Transfer_W_" + bndryName + " Monitor"));
      heatpSATrReportMonitors.add(simMonMngr.getMonitor("Heat_Transfer_AreaAveraged_Wm2_" + bndryName + " Monitor"));
      forcesReportMonitors.add(simMonMngr.getMonitor("Force_" + bndryName + " Monitor"));
      enthalReportMonitors.add(simMonMngr.getMonitor("TotalEnthalpy_" + bndryName + " Monitor"));
      radheatTransferReportMonitors.add(simMonMngr.getMonitor("Heat_Flux_Radiation_" + bndryName + " Monitor"));
      condheatTransferReportMonitors.add(simMonMngr.getMonitor("Heat_Flux_Conduction_" + bndryName + " Monitor"));
    }

    simPlotMngr.createMonitorPlot(temperReportMonitors, "Pipe Temperatures Plot");
    simPlotMngr.createMonitorPlot(heatTrReportMonitors, "Heat Transfer through Pipe Plot");
    simPlotMngr.createMonitorPlot(heatpSATrReportMonitors, "Heat Transfer Per Surface Area on Pipes Plot");
    simPlotMngr.createMonitorPlot(forcesReportMonitors, "Forces on Pipes Plot");
    simPlotMngr.createMonitorPlot(enthalReportMonitors, "Total Enthalpy on Pipes Plot");
    simPlotMngr.createMonitorPlot(radheatTransferReportMonitors, "Heat Transfer by Radiation on Pipes Plot");
    simPlotMngr.createMonitorPlot(condheatTransferReportMonitors, "Heat Transfer by Conduction on Pipes Plot");


    // Adjusting Scale of "Pipe Temperatures Plot"
    MonitorPlot temperaturesPlot = ((MonitorPlot) sim.getPlotManager().getPlot("Pipe Temperatures Plot"));
    Cartesian2DAxisManager cartesian2DAxisManager_0 = ((Cartesian2DAxisManager) temperaturesPlot.getAxisManager());
    Cartesian2DAxis cartesian2DAxis_0 = ((Cartesian2DAxis) cartesian2DAxisManager_0.getAxis("Left Axis"));
    cartesian2DAxis_0.setMaximum(1500.0);
    cartesian2DAxis_0.setMinimum(800.0);

    // Create Report for Temperature at the Outlet
    MassFlowAverageReport massFlowAverageReport_temperatureAtOutlet = simRepMngr.createReport(MassFlowAverageReport.class);
    massFlowAverageReport_temperatureAtOutlet.setPresentationName("Temperature_Outlet");
    massFlowAverageReport_temperatureAtOutlet.setFieldFunction(ff_Temperature);
    massFlowAverageReport_temperatureAtOutlet.getParts().setQuery(null);
    massFlowAverageReport_temperatureAtOutlet.getParts().setObjects(bdry_outlet);
    simMonMngr.createMonitorAndPlot(new NeoObjectVector(new Object[]{massFlowAverageReport_temperatureAtOutlet}), true, "Temperature at Outlet Plot");
    
    ReportMonitor massFlowAverageReport_temperatureAtOutletMonitor
            = ((ReportMonitor) simMonMngr.getMonitor(massFlowAverageReport_temperatureAtOutlet.getPresentationName() + " Monitor"));
    simPlotMngr.createMonitorPlot(new NeoObjectVector(new Object[]{massFlowAverageReport_temperatureAtOutletMonitor}), "Temperature at Outlet Plot");
    
    
    
    // create Pressure Drop Report
    PressureDropReport pDropReport = simRepMngr.createReport(PressureDropReport.class);
    pDropReport.getParts().setQuery(null);
    pDropReport.getParts().setObjects(bdry_inlet);
    pDropReport.getLowPressureParts().setQuery(null);
    pDropReport.getLowPressureParts().setObjects(bdry_outlet);
    pDropReport.setPresentationName("Pressure_Drop");
    simMonMngr.createMonitorAndPlot(new NeoObjectVector(new Object[]{pDropReport}), true, "Pressure Drop Plot");
  
    ReportMonitor pDropReportMonitor
            = ((ReportMonitor) simMonMngr.getMonitor(pDropReport.getPresentationName() + " Monitor"));
    simPlotMngr.createMonitorPlot(new NeoObjectVector(new Object[]{pDropReportMonitor}), "Pressure Drop Plot");
        
    
    // Heat Transfer Balance between Inlet and Outlet
    HeatTransferReport heatTransferReport_Balance = simRepMngr.createReport(HeatTransferReport.class);
    heatTransferReport_Balance.getParts().setQuery(null);
    heatTransferReport_Balance.getParts().setObjects(bdry_inlet, bdry_outlet);
    heatTransferReport_Balance.setPresentationName("Heat_Transfer_Balance_InletOutlet");

    simMonMngr.createMonitorAndPlot(new NeoObjectVector(new Object[]{heatTransferReport_Balance}), true, "%1$s Plot");

    ReportMonitor heatTransferReport_BalanceMonitor
            = ((ReportMonitor) simMonMngr.getMonitor(heatTransferReport_Balance.getPresentationName() + " Monitor"));

    MonitorPlot monitorPlot_htBalance
            = simPlotMngr.createMonitorPlot(new NeoObjectVector(new Object[]{heatTransferReport_BalanceMonitor}), "Heat_Transfer_Balance_InletOutlet Plot");

    // Total Enthalpy at inlet and outlet
    for (Boundary bndry : inoutList) {
      String bndryName = bndry.getPresentationName();

      AreaAverageReport areaAverageReport = simRepMngr.createReport(AreaAverageReport.class);
      areaAverageReport.setFieldFunction(ff_Enthalpy);
      areaAverageReport.getParts().setQuery(null);
      areaAverageReport.getParts().setObjects(bndry);
      areaAverageReport.setPresentationName("TotalEnthalpy_" + bndryName);

      simMonMngr.createMonitorAndPlot(new NeoObjectVector(new Object[]{areaAverageReport}), true, "%1$s Plot");

      ReportMonitor enthalpyMonitor
              = ((ReportMonitor) simMonMngr.getMonitor(areaAverageReport.getPresentationName() + " Monitor"));

      MonitorPlot enthalphyMonitorPlot
              = simPlotMngr.createMonitorPlot(new NeoObjectVector(new Object[]{enthalpyMonitor}), areaAverageReport.getPresentationName() + " Plot");

    }

  }

  private void setSolverSettings() {
    sim = getActiveSimulation();

    CoupledImplicitSolver coupledImplicitSolver_0 = ((CoupledImplicitSolver) sim.getSolverManager().getSolver(CoupledImplicitSolver.class));
    coupledImplicitSolver_0.getRampCalculatorManager().getRampCalculatorOption().setSelected(RampCalculatorOption.Type.LINEAR_RAMP);

    LinearRampCalculator linearRampCalculator_0 = ((LinearRampCalculator) coupledImplicitSolver_0.getRampCalculatorManager().getCalculator());
    linearRampCalculator_0.setEndIteration(2000);

    coupledImplicitSolver_0.setCFL(100.0);

//    coupledImplicitSolver_0.getExpertInitManager().getExpertInitOption().setSelected(ExpertInitOption.Type.GRID_SEQ_METHOD);
    
    
    // Radiation adatpion
    

    DORadiationSolver dORadiationSolver_0 = ((DORadiationSolver) sim.getSolverManager().getSolver(DORadiationSolver.class));
    dORadiationSolver_0.setUrf(0.95);
    
    AMGLinearSolver aMGLinearSolver_0 = dORadiationSolver_0.getAMGLinearSolver();
    aMGLinearSolver_0.getCycleOption().setSelected(AMGCycleOption.Type.FLEX_CYCLE);


  }

  private void setStoppingCriteria() {
    sim = getActiveSimulation();

    MonitorManager simMonMngr = sim.getMonitorManager();

    // Generating Stopping Criteria for the following list
    String[] stopCrits = {"Continuity", "X-momentum", "Y-momentum", "Energy", "Tdr", "Tke", "CO2", "H2O", "N2", "O2"};

    for (String stop : stopCrits) {
      Monitor mon = simMonMngr.getMonitor(stop);

      MonitorIterationStoppingCriterion itStpCrt = mon.createIterationStoppingCriterion();

      itStpCrt.getLogicalOption().setSelected(SolverStoppingCriterionLogicalOption.Type.AND);

      MonitorIterationStoppingCriterionMinLimitType itStpCrtLim
              = ((MonitorIterationStoppingCriterionMinLimitType) itStpCrt.getCriterionType());

      itStpCrtLim.getLimit().setValue(0.001);
    }

    // Setting asympt Stopping Criterion for Heat Transfer Balance
    ReportMonitor reportMonitor_0= ((ReportMonitor) simMonMngr.getMonitor("Heat_Transfer_Balance_InletOutlet Monitor"));
    MonitorIterationStoppingCriterion iterationStopCriterion= reportMonitor_0.createIterationStoppingCriterion();
    ((MonitorIterationStoppingCriterionOption) iterationStopCriterion.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.ASYMPTOTIC);
    MonitorIterationStoppingCriterionAsymptoticType monitorIterationStoppingCriterionAsymptoticType_0
            = ((MonitorIterationStoppingCriterionAsymptoticType) iterationStopCriterion.getCriterionType());
    monitorIterationStoppingCriterionAsymptoticType_0.setNumberSamples(100);
    iterationStopCriterion.getLogicalOption().setSelected(SolverStoppingCriterionLogicalOption.Type.AND);
    
    MonitorIterationStoppingCriterion monitorIterationStoppingCriterion_0 = 
      ((MonitorIterationStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Heat_Transfer_Balance_InletOutlet Monitor Criterion"));

    ((MonitorIterationStoppingCriterionOption) monitorIterationStoppingCriterion_0.getCriterionOption()).setSelected(MonitorIterationStoppingCriterionOption.Type.RELATIVE_CHANGE);

    MonitorIterationStoppingCriterionRelativeChangeType monitorIterationStoppingCriterionRelativeChangeType_0 = 
      ((MonitorIterationStoppingCriterionRelativeChangeType) monitorIterationStoppingCriterion_0.getCriterionType());

    monitorIterationStoppingCriterionRelativeChangeType_0.setRelativeChange(1.0E-4);
    

    // Setting Max steps
    StepStoppingCriterion stepStoppingCriterion_0
            = ((StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps"));

    stepStoppingCriterion_0.setMaximumNumberSteps(SIM_MAXSTEPS_ALLOWED);

  }

  private void setScenes() {
    sim = getActiveSimulation();

    msg("Setting the Scenes.");

    FieldFunctionManager simFFMngr = sim.getFieldFunctionManager();

    // Pressure
    ScalarDisplayer pDsplr = createNewScalarScene("Pressure");
    FieldFunction ff_p = simFFMngr.getFunction("Pressure");
    pDsplr.getScalarDisplayQuantity().setFieldFunction(ff_p);

    // Velocity
    ScalarDisplayer velDsplr = createNewScalarScene("Velocity");
    FieldFunction ff_vel = simFFMngr.getFunction("Velocity").getMagnitudeFunction();
    velDsplr.getScalarDisplayQuantity().setFieldFunction(ff_vel);

    // Viscosity
    ScalarDisplayer viscDsplr = createNewScalarScene("DynamicViscosity");
    FieldFunction ff_visc = simFFMngr.getFunction("DynamicViscosity");
    viscDsplr.getScalarDisplayQuantity().setFieldFunction(ff_visc);

    // Density
    ScalarDisplayer rhoDsplr = createNewScalarScene("Density");
    FieldFunction ff_rho = simFFMngr.getFunction("Density");
    rhoDsplr.getScalarDisplayQuantity().setFieldFunction(ff_rho);

    //Temperature
    ScalarDisplayer tmpDsplr = createNewScalarScene("Temperature");
    FieldFunction ff_tmp = simFFMngr.getFunction("Temperature");
    tmpDsplr.getScalarDisplayQuantity().setFieldFunction(ff_tmp);
    tmpDsplr.getScalarDisplayQuantity().setRange(new DoubleVector(new double[]{800.0, 1800.0}));
    tmpDsplr.getScalarDisplayQuantity().setClip(ClipMode.NONE);

    //Total Enthalpy
    ScalarDisplayer entDsplr = createNewScalarScene("TotalEnthalpy");
    FieldFunction ff_ent = simFFMngr.getFunction("TotalEnthalpy");
    entDsplr.getScalarDisplayQuantity().setFieldFunction(ff_ent);

    //Incident Radiation
    ScalarDisplayer incidentRadiatDsplr = createNewScalarScene("IncidentRadiation");
    FieldFunction ff_incidRad = simFFMngr.getFunction("IncidentRadiation");
    incidentRadiatDsplr.getScalarDisplayQuantity().setFieldFunction(ff_incidRad);
    incidentRadiatDsplr.getScalarDisplayQuantity().setRange(new DoubleVector(new double[]{90000.0, 900000.0}));
    incidentRadiatDsplr.getScalarDisplayQuantity().setClip(ClipMode.NONE);

    //Vorticity
    ScalarDisplayer vorDsplr = createNewScalarScene("Vorticity");
    FieldFunction ff_vor = simFFMngr.getFunction("VorticityVector").getMagnitudeFunction();
    vorDsplr.getScalarDisplayQuantity().setFieldFunction(ff_vor);
    vorDsplr.getLegend().setScaleMode(LutScale.LOG10);

    //Turbulent Kinetic Energy
    ScalarDisplayer tkeDsplr = createNewScalarScene("TurbulentKineticEnergy");
    FieldFunction ff_tke = simFFMngr.getFunction("TurbulentKineticEnergy");
    tkeDsplr.getScalarDisplayQuantity().setFieldFunction(ff_tke);
    tkeDsplr.getLegend().setScaleMode(LutScale.LOG10);

    //Turbulent Dissipation Rate
    ScalarDisplayer tdrDsplr = createNewScalarScene("TurbulentDissipationRate");
    FieldFunction ff_tdr = simFFMngr.getFunction("TurbulentDissipationRate");
    tdrDsplr.getScalarDisplayQuantity().setFieldFunction(ff_tdr);
    tdrDsplr.getLegend().setScaleMode(LutScale.LOG10);

    //Turbulent Dissipation Rate
    ScalarDisplayer tvDsplr = createNewScalarScene("TurbulentViscosity");
    FieldFunction ff_tv = simFFMngr.getFunction("TurbulentViscosity");
    tvDsplr.getScalarDisplayQuantity().setFieldFunction(ff_tv);

    //Total Energy
    ScalarDisplayer totalEnergyDsplr = createNewScalarScene("TotalEnergy");
    FieldFunction ff_totalEnergy = simFFMngr.getFunction("TotalEnergy");
    totalEnergyDsplr.getScalarDisplayQuantity().setFieldFunction(ff_totalEnergy);

    // Least Square Cell Quality
    ScalarDisplayer cqDsplr = createNewScalarScene("LeastSquaresQuality");
    FieldFunction ff_cq = simFFMngr.getFunction("LeastSquaresQuality");
    cqDsplr.getScalarDisplayQuantity().setFieldFunction(ff_cq);

    // Geo
    sim.getSceneManager().createGeometryScene("Geometry Scene", "Outline", "Geometry", 1);
    Scene scene_geo = sim.getSceneManager().getScene("Geometry Scene 1");
    scene_geo.setPresentationName("Geometry");
    CurrentView currentView_geo = scene_geo.getCurrentView();
    currentView_geo.setInput(new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.0}), new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.4}), new DoubleVector(new double[]{0.0, 1.0, 0.0}), 0.20, 0);

    // Mesh
    sim.getSceneManager().createGeometryScene("Mesh Scene", "Outline", "Mesh", 3);
    Scene scene_mesh = sim.getSceneManager().getScene("Mesh Scene 1");
    scene_mesh.setPresentationName("Mesh");
    CurrentView currentView_mesh = scene_mesh.getCurrentView();
    currentView_mesh.setInput(new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.0}), new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.4}), new DoubleVector(new double[]{0.0, 1.0, 0.0}), 0.20, 0);

  }

  // Creates new scene by name and returns the Scalar Displayer
  private ScalarDisplayer createNewScalarScene(String sceneName) {
    sim = getActiveSimulation();

    sim.getSceneManager().createScalarScene("Scalar Scene", "Outline", "Scalar");

    Scene scene = sim.getSceneManager().getScene("Scalar Scene 1");
    scene.setPresentationName(sceneName);
//        msg("Created " + scene.getPresentationName() + " Scene");
    CurrentView currentView_1 = scene.getCurrentView();

    currentView_1.setInput(new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.0}), new DoubleVector(new double[]{0.0, 0.05000000000000002, 0.4}), new DoubleVector(new double[]{0.0, 1.0, 0.0}), 0.20, 0);
    ScalarDisplayer scalarDisplayer = ((ScalarDisplayer) scene.getDisplayerManager().getDisplayer("Scalar 1"));
    
    // Set Update Frequency to 10
    scene.getSceneUpdate().getIterationUpdateFrequency().setIterations(10);

//        msg("Returning " + scene.getPresentationName() + "'s " + scalarDisplayer.getPresentationName() + " Scalar Displayer");
    return scalarDisplayer;
  }

  private void runSim() {
    sim = getActiveSimulation();

    msg("Running the Simulation.");


        // Initialization of special Stopping Criterion
        SolverStoppingCriterion maxStepCriterion = sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        SolverStoppingCriterion stopFileCriterion = sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Stop File");
 
        Collection<SolverStoppingCriterion> criterions = new ArrayList<>();

        // Fill criterion collection while filtering out stop file and max step criterion
        sim.getSolverStoppingCriterionManager()
                .getObjects().stream()
                .filter(crit -> !crit.equals(maxStepCriterion))
                .filter(crit -> !crit.equals(stopFileCriterion))
                .forEach(crit -> criterions.add(crit));
 
         // Solve while criterions are true.

    try {
      sim.getSimulationIterator().step(SIM_MINSTEPS);  
        while (timeLeft()
                && maxStepCriterion.getIsUsed() ? !maxStepCriterion.getIsSatisfied() : true // check max steps, only if criterion is enabled, else continue
                && !stopFileCriterion.getIsSatisfied() // check if stop file is present
                && criterions.size() == 0 ? true      // continue if no additional criterion defined, else check criterions
                : (criterions.stream()
                        .filter(crit -> crit.getIsUsed()) // check only enabled criterions
                        .filter(crit -> !crit.getIsSatisfied()) // count only unsatisfied criterions
                        .count() > 0)) // when no unsatisfied criterions are left then sim has finished
        {
            sim.getSimulationIterator().step(5);
        }
    } catch (Exception e) {
      msgErr(e.toString());
    }
  }

  // Final save. Saves everything after simulation has run.
  private void saveAll() {
    sim = getActiveSimulation();

    msg("Saving the Simulation, Scenes and Plots.");

    sim.saveState(resolvePath(SIM_FILENAME + ".sim"));

    // sim.saveState(resolvePath(sim.getPresentationName()  + ".sim"));
    saveScenes();
    savePlots();
    saveReportsValues();
    saveStatistics_Enthalpy();
    saveStatistics_HeatTransferPerSurfaceArea();
    saveStatistics_HeatTransfer();

  }

  // Saving all Scenes with optional path    
  private void saveScenes(String path) {
    sim = getActiveSimulation();

    for (Scene scene : sim.getSceneManager().getScenes()) {
      msg("Saving Scene: " + scene.getPresentationName());

      fitViewOfScene(scene);
      scene.printAndWait(resolvePath(path + "scn_" + scene.getPresentationName() + ".jpg"), 4, 800, 600);

    }
  }

  private void saveScenes() {
    saveScenes("");
  }

  // Saving all Plots with optional path
  private void savePlots(String path) {
    sim = getActiveSimulation();

    for (StarPlot plot : sim.getPlotManager().getObjects()) {
      msg("Saving plot: " + plot.getPresentationName());

      plot.encode(resolvePath(path + "plt_" + plot.getPresentationName() + ".jpg"), "jpg", 800, 600);

    }

    resolvePath("");
  }

  private void savePlots() {
    savePlots("");
  }

  private void saveReportsValues() {
    sim = getActiveSimulation();

    for (Report rep : sim.getReportManager().getObjects()) {
      msg("Saving Report: " + rep.getPresentationName());

      saveValueToFile(String.valueOf(rep.monitoredValue()), "rep_" + rep.getPresentationName() + ".val");
    }

  }

  private void saveStatistics_Enthalpy() {
    sim = getActiveSimulation();

    Region reg = sim.getRegionManager().getRegion("Base");

    Collection<Boundary> boundaries = reg.getBoundaryManager().getBoundaries();
    Collection<Boundary> pipesBndryList = new ArrayList<>();

    for (Boundary bndry : boundaries) {
      if (bndry.getBoundaryType() instanceof WallBoundary) {
        if (bndry.getPresentationName().matches("^PWALL_.*")) {
          pipesBndryList.add(bndry);
        }
      }
    }

    double[] raw_data = new double[pipesBndryList.size()];

    int i = 0;
    for (Boundary bndry : pipesBndryList) {
      String bndryName = bndry.getPresentationName();
      Report rep = sim.getReportManager().getReport("TotalEnthalpy_" + bndryName);

      raw_data[i] = rep.monitoredValue();

      i++;
    }

    Statistics stat = new Statistics(raw_data);

    saveValueToFile(String.valueOf(stat.getMean()), "rep_Enthalpy_mean.val");
    saveValueToFile(String.valueOf(stat.getVariance()), "rep_Enthalpy_variance.val");
    saveValueToFile(String.valueOf(stat.getStdDev()), "rep_Enthalpy_std.val");

    msg("rep_rep_Enthalpy_std Statistics written");

  }

  private void saveStatistics_HeatTransferPerSurfaceArea() {
    sim = getActiveSimulation();

    Region reg = sim.getRegionManager().getRegion("Base");

    Collection<Boundary> boundaries = reg.getBoundaryManager().getBoundaries();
    Collection<Boundary> pipesBndryList = new ArrayList<>();

    for (Boundary bndry : boundaries) {
      if (bndry.getBoundaryType() instanceof WallBoundary) {
        if (bndry.getPresentationName().matches("^PWALL_.*")) {
          pipesBndryList.add(bndry);
        }
      }
    }

    double[] raw_data = new double[pipesBndryList.size()];

    int i = 0;
    for (Boundary bndry : pipesBndryList) {
      String bndryName = bndry.getPresentationName();
      Report rep = sim.getReportManager().getReport("Heat_Transfer_AreaAveraged_Wm2_" + bndryName);

      raw_data[i] = rep.monitoredValue();

      i++;
    }

    Statistics stat = new Statistics(raw_data);

    saveValueToFile(String.valueOf(stat.getMean()), "rep_Heat_Transfer_AreaAveraged_Wm2_mean.val");
    saveValueToFile(String.valueOf(stat.getVariance()), "rep_Heat_Transfer_AreaAveraged_Wm2_variance.val");
    saveValueToFile(String.valueOf(stat.getStdDev()), "rep_Heat_Transfer_AreaAveraged_Wm2_std.val");

    msg("rep_Heat_Transfer_AreaAveraged Statistics written");

  }

  private void saveStatistics_HeatTransfer() {
    sim = getActiveSimulation();

    Region reg = sim.getRegionManager().getRegion("Base");

    Collection<Boundary> boundaries = reg.getBoundaryManager().getBoundaries();
    Collection<Boundary> pipesBndryList = new ArrayList<>();

    for (Boundary bndry : boundaries) {
      if (bndry.getBoundaryType() instanceof WallBoundary) {
        if (bndry.getPresentationName().matches("^PWALL_.*")) {
          pipesBndryList.add(bndry);
        }
      }
    }

    double[] raw_data = new double[pipesBndryList.size()];

    int i = 0;
    for (Boundary bndry : pipesBndryList) {
      String bndryName = bndry.getPresentationName();
      Report rep = sim.getReportManager().getReport("Heat_Transfer_W_" + bndryName);

      raw_data[i] = rep.monitoredValue();

      i++;
    }

    Statistics stat = new Statistics(raw_data);

    saveValueToFile(String.valueOf(stat.getMean()), "rep_Heat_Transfer_W_mean.val");
    saveValueToFile(String.valueOf(stat.getVariance()), "rep_Heat_Transfer_W_variance.val");
    saveValueToFile(String.valueOf(stat.getStdDev()), "rep_Heat_Transfer_W_std.val");

    msg("rep_Heat_Transfer_W Statistics written");

  }

  // Reading one double value from txt file
  private Double readDoubleFromFile(String valFile) {
    String line;
    Double value = null;

    sim = getActiveSimulation();

    msg("Reading value file.");
    try (BufferedReader br = new BufferedReader(new FileReader(valFile))) {
        line = br.readLine();
      if ( line != null) {
        value = Double.valueOf(line);
      } else {
        msgErr("Input value was missing in input file.");
      }
    } catch (IOException e) {
      msgErr(e.toString());
    }

    if (value == null) {
      msgErr("Reading value input failed.");
    }

    return value;
  }

  // Reading in coordinates from csv file and return list of coordinate tuples (x,y)
  private List<double[]> read_pipe_coordinates(String csvFile) {

    sim = getActiveSimulation();
    msg("Loading pipe coordinates");

    List<double[]> return_coordinates = new ArrayList<>();
    String line;
    String cvsSplitBy = ",";

    try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
      while ((line = br.readLine()) != null) {
        // use comma as separator
        List<String> values = Arrays.asList(line.split(cvsSplitBy));

        double[] currentrow = new double[2];

        switch (values.size()) {
          case 1:
            currentrow[0] = Double.valueOf(values.get(0));
            currentrow[1] = 0.0;
            break;
          case 2:
            currentrow[0] = Double.valueOf(values.get(0));
            currentrow[1] = Double.valueOf(values.get(1));
            break;
          default:
            continue;
        }
        return_coordinates.add(currentrow);
      }

    } catch (IOException e) {
      msgErr(e.toString());
    }
    return return_coordinates;
  }

  /* Creates a sketch in the xy plane in a given CAD model and opens it*/
  private Sketch create_sketch_xy(String sketchname, Simulation sim, CadModel param_cadModel) {
//        sim.println("Getting xy_plane");      
    CanonicalSketchPlane xy_plane = ((CanonicalSketchPlane) param_cadModel.getFeature("XY"));

//        sim.println("Creating sketch " + sketchname);
    Sketch sketch = param_cadModel.getFeatureManager().createSketch(xy_plane);
//        sim.println("Naming sketch " + sketchname);
    sketch.setPresentationName(sketchname);

    return sketch;
  }

  private ExtrusionMerge extrude_woMerge(Sketch sketch, Simulation sim, CadModel param_cadModel) {

    Units units_m = sim.getUnitsManager().getPreferredUnits(new IntVector(new int[]{0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));

    ExtrusionMerge em = param_cadModel.getFeatureManager().createExtrusionMerge(sketch);
    em.setDirectionOption(0);
    em.setExtrudedBodyTypeOption(0);
    em.setDistanceOption(0);
    em.setCoordinateSystemOption(0);
    em.setDraftOption(0);

    LabCoordinateSystem labCoordinateSystem_0 = sim.getCoordinateSystemManager().getLabCoordinateSystem();
    em.setImportedCoordinateSystem(labCoordinateSystem_0);

    CadModelCoordinate cadModelCoordinate_0 = em.getDirectionAxis();
    cadModelCoordinate_0.setCoordinateSystem(labCoordinateSystem_0);

    cadModelCoordinate_0.setCoordinate(units_m, units_m, units_m, new DoubleVector(new double[]{0.0, 0.0, 1.0}));

    em.setFace(null);
    em.setBody(null);
    em.setSketch(sketch);
    em.setPostOption(0);
    em.setExtrusionOption(0);
    BodyNameRefManager bodyNameRefManager_0 = em.getInteractionBodies();
    bodyNameRefManager_0.setBodies(new NeoObjectVector(new Object[]{}));
    em.setInteractingSelectedBodies(false);
    em.markFeatureForEdit();
    param_cadModel.getFeatureManager().execute(em);

    em.setPresentationName("Extrusion_" + sketch.getPresentationName());

    return em;
  }

  // Check whether there are any intersections or short distances between pipes.
  // It returns false if there are no intersections. True if intersections have been found.
  private void arePipesIntersectingEachOther() {
    double p_distance;
    msg("Checking whether two pipes are intersecting with each other.");

    for (int i = 0; i < pipecenters.size(); i++) {
      for (int j = i + 1; j < pipecenters.size(); j++) {
        p_distance = Math.sqrt(Math.pow(pipecenters.get(i)[0] - pipecenters.get(j)[0], 2) + Math.pow(pipecenters.get(i)[1] - pipecenters.get(j)[1], 2));

        if (p_distance < PIPE_SAFEDISTANCE + 2 * PIPE_RADIUS) {
          msgErr("Pipe_" + (i + 1) + " is intersecting with Pipe_" + (j + 1));
        }

      }
    }
  }

  private void arePipesIntersectingWalls() {
    boolean bl, bt, br, bb;
    msg("Checking for Pipe-Wall-Interactions");

    for (int i = 0; i < pipecenters.size(); i++) {
      bl = distancePointLine(pipecenters.get(i), mainCornerPoint_SW, mainCornerPoint_NW) < PIPE_SAFEDISTANCE + PIPE_RADIUS;
      bt = distancePointLine(pipecenters.get(i), mainCornerPoint_NW, mainCornerPoint_NE) < PIPE_SAFEDISTANCE + PIPE_RADIUS;
      br = distancePointLine(pipecenters.get(i), mainCornerPoint_NE, mainCornerPoint_SE) < PIPE_SAFEDISTANCE + PIPE_RADIUS;
      bb = distancePointLine(pipecenters.get(i), mainCornerPoint_SE, mainCornerPoint_SW) < PIPE_SAFEDISTANCE + PIPE_RADIUS;

      if (bl || br || bt || bb) {
        msgErr("Pipe_" + (i + 1) + " is too close to the wall.");
      }
    }
  }

  private void arePipesInsideDomain() {
    boolean bl, bt, br, bb;
    msg("Checking whether all Pipes are inside the domain.");

    for (int i = 0; i < pipecenters.size(); i++) {
      bl = isPointLeftOfLine(pipecenters.get(i), mainCornerPoint_NW, mainCornerPoint_SW);
      bt = isPointLeftOfLine(pipecenters.get(i), mainCornerPoint_NE, mainCornerPoint_NW);
      br = isPointLeftOfLine(pipecenters.get(i), mainCornerPoint_SE, mainCornerPoint_NE);
      bb = isPointLeftOfLine(pipecenters.get(i), mainCornerPoint_SW, mainCornerPoint_SE);

      if (bl || br || bt || bb) {
        msgErr("Pipe_" + (i + 1) + " is not inside the domain. Left:" + bl + " Top:" + bt + " Right:" + br + " Bottom:" + bb);
      }
    }
  }

  /**
   * Some Math methods ahead
   *
   */
  // Calculates normal distance between a point and a line - given by two points
  private double distancePointLine(double[] point, double[] line_point1, double[] line_point2) {
    double p0x = point[0];
    double p0y = point[1];
    double p1x = line_point1[0];
    double p1y = line_point1[1];
    double p2x = line_point2[0];
    double p2y = line_point2[1];

    double num = Math.abs((p2y - p1y) * p0x - (p2x - p1x) * p0y + p2x * p1y - p2y * p1x);
    double den = Math.sqrt(Math.pow(p2y - p1y, 2) + Math.pow(p2x - p1x, 2));

    return num / den;
  }

  // checking if point is left of a line (given by two points)
  private boolean isPointLeftOfLine(double[] point, double[] line_point1, double[] line_point2) {

    double p0x = point[0];
    double p0y = point[1];
    double p1x = line_point1[0];
    double p1y = line_point1[1];
    double p2x = line_point2[0];
    double p2y = line_point2[1];

    return ((p2x - p1x) * (p0y - p1y) - (p2y - p1y) * (p0x - p1x)) >= 0;
  }

  private void msgErr(String msg) {
    sim = getActiveSimulation();
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Date date = new Date();

    sim.println("------------------------------------------------------------");
    sim.println("-------------------------- ERROR ---------------------------");
    sim.println(dateFormat.format(date) + ": " + msg);
    sim.println("------------------------------------------------------------");
  }

  private void msg(String msg) {
    sim = getActiveSimulation();
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Date date = new Date();

    sim.println(dateFormat.format(date) + ": " + msg);
  }

  private Object[] appendObject(Object[] obj, Object newObj) {

    ArrayList<Object> temp = new ArrayList<>(Arrays.asList(obj));
    temp.add(newObj);
    return temp.toArray();

  }

  private void saveReportValueToFile(String reportName, String fileName) {
    sim = getActiveSimulation();
    Report rep = sim.getReportManager().getReport(reportName);
    String repVal = String.valueOf(rep.monitoredValue());

    BufferedWriter writer = null;

    try {
      writer = new BufferedWriter(new FileWriter(fileName));
      writer.write(repVal);
    } catch (IOException e) {
      msgErr(e.toString());
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException e) {
        msgErr(e.toString());
      }
    }

  }

  private void saveValueToFile(String val, String fileName) {
    BufferedWriter writer = null;

    try {
      writer = new BufferedWriter(new FileWriter(fileName));
      writer.write(val);

    } catch (IOException e) {
      msgErr(e.toString());
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (IOException e) {
        msgErr(e.toString());
      }
    }
  }

  private class Statistics {

    double[] data;
    int size;

    public Statistics(double[] data) {
      this.data = data;
      size = data.length;
    }

    double getMean() {
      double sum = 0.0;
      for (double a : data) {
        sum += a;
      }
      return sum / size;
    }

    double getVariance() {
      double mean = getMean();
      double temp = 0;
      for (double a : data) {
        temp += (a - mean) * (a - mean);
      }
      return temp / (size - 1);
    }

    double getStdDev() {
      return Math.sqrt(getVariance());
    }
  }

  private void fitViewOfScene(Scene sce) {
    sce.getCurrentView().setProjectionMode(VisProjectionMode.PARALLEL);
    sce.setViewOrientation(new DoubleVector(new double[]{1, 0, 0}), new DoubleVector(new double[]{0, 1, 0}));
    sce.resetCamera();

    Displayer displayer = sce.getDisplayerManager().hasObject("Scalar 1");

    //Grabbing all the parts inside of the displayer Scalar 1   
    if (displayer != null) {
      Collection<NamedObject> pg = sce.getDisplayerManager().getDisplayer("Scalar 1").getInputParts().getParts();

      double xRange = sce.computeBoundsForParts(pg).computeRange(0);
      double yRange = sce.computeBoundsForParts(pg).computeRange(1);
      double zRange = sce.computeBoundsForParts(pg).computeRange(2);

      double hPS = (yRange / 1.5);  //Y parallel scale

      sce.getCurrentView().setParallelScale(hPS);
    }
  }

    private boolean timeLeft() {
        /* ######################################### */
        // buffertime in milliseconds
        long bufferTime = 10 * 60 * 1000;
        /* ######################################### */
 
        //Initialization of variables
        long diffTime = 0;
        long finalTime = 0;
 
        // Get current time stamp
        long currentTime = System.currentTimeMillis();
 
        // Get remaining time of current job
        String[] command = {"/bin/sh", "-c", "date '+%s' -d $(scontrol show job $SLURM_JOBID | grep EndTime | cut -d'=' -f3 | cut -d' ' -f1)"};
        try {
            Process proc = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String s;
            while ((s = reader.readLine()) != null) {
                finalTime = Long.valueOf(s).longValue() * 1000; // in milliseconds
            }
        } catch (IOException ex) {
            return false;
        } // stop if something went wrong here
 
        //calculate remaining time
        diffTime = finalTime - currentTime;
 
        // decide whether enough time is left
        if (diffTime > bufferTime) {
            return true;
        } else {
            Simulation sim = getActiveSimulation();
            sim.println("WARNING: The job has run out of time.");
            return false;
        }
    }

}
