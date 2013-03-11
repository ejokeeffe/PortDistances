/**
 * @brief This class builds the port to port distances using the gephi toolkit. 
 * 
 * @author Eoin O'Keeffe
 * 
 * @version 1.0
 * 
 * @date 04/03/2012
 * 
 */
package eok.modelling;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.apache.commons.collections.keyvalue.MultiKey;

import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.NodeData;
import org.gephi.graph.api.UndirectedGraph;
import org.gephi.graph.dhns.node.NodeDataImpl;
import org.gephi.io.database.drivers.PostgreSQLDriver;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.plugin.database.EdgeListDatabaseImpl;
import org.gephi.io.importer.plugin.database.ImporterEdgeList;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
import org.gephi.algorithms.shortestpath.DijkstraShortestPathAlgorithm;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import edu.umbc.cs.maple.utils.ColtUtils;
import eok.database.DatabasePG;
import eok.generics.Pair;
import eok.generics.Useful;
import eok.location.Port;

public class BuildPortDistances {
	private ArrayList<Port> p_list;
	private HashMap<Port,Node> portNodes;
	private String dbUser;
	private String dbPass;
	private GraphModel graphModel;
	private Map<MultiKey,Node> nodeLocationsMap;
	private Map<MultiKey,Double> portToPortDistances;
	private DenseDoubleMatrix2D nodeLocations;
	private ProjectController pc;
	/** @brief Its all rung from this class
	 * 
	 * @param args
	 */
	public BuildPortDistances(){
		p_list = new ArrayList<Port>();
	}//constructor
	/**
	 * @brief this reads the node and edges for the base grid
	 * 
	 */
	public void buildBaseGrid(String nodesTable,String edgesTable){
		pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        Workspace workspace = pc.getCurrentWorkspace();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
	        
		 //Import database
        EdgeListDatabaseImpl db = new EdgeListDatabaseImpl();
        db.setDBName("Gephi");
        db.setHost("localhost");
        db.setUsername(this.dbUser);
        db.setPasswd(this.dbPass);
        db.setSQLDriver(new PostgreSQLDriver());
        //db.setSQLDriver(new PostgreSQLDriver());
        //db.setSQLDriver(new SQLServerDriver());
        db.setPort(5432);
        db.setNodeQuery("SELECT nodes.id AS id, nodes.label as label,nodes.x as x,nodes.y as y,nodes.longitude as longitude, nodes.latitude as latitude FROM \"" + nodesTable + "\" as nodes");
        db.setEdgeQuery("SELECT edges.source AS source, edges.target AS target, edges.weight AS weight FROM \"" + edgesTable + "\" as edges");
        ImporterEdgeList edgeListImporter = new ImporterEdgeList();
        Container container = importController.importDatabase(db, edgeListImporter);
        container.setAllowAutoNode(false);      //Don't create missing nodes
        container.getLoader().setEdgeDefault(EdgeDefault.DIRECTED);   //Force unDirected
        
      //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);
        
      //  UndirectedGraph graph = graphModel.getUndirectedGraph();
      //  System.out.println("Nodes: " + graph.getNodeCount());
      //  System.out.println("Edges: " + graph.getEdgeCount());
	}//buildBaseGrid
	
	/**
	 * @brief for each port, a node is created. Then the distance is found to every other node in the 
	 * graph (great circle distance distance). The min distance is then set as the entry node and this node is connected to our new port node.
	 * 
	 * Great circle distance: 
	 *  
	 * @param list The list of ports for which we are getting distances for
	 * 
	 */
	public void addPortsToGrid(ArrayList<Port> list){
		//need to instantiate a few variables
		nodeLocations = new DenseDoubleMatrix2D(graphModel.getUndirectedGraph().getNodeCount(),2);
		nodeLocationsMap = new HashMap<MultiKey,Node>();
		this.portNodes = new HashMap<Port,Node>();
		this.p_list=(ArrayList<Port>) list;
		//Loop through - first id is 1
		for(int i=1;i<=graphModel.getUndirectedGraph().getNodeCount();i++){
			
			Node nd = graphModel.getUndirectedGraph().getNode(i);
			Double lon = (Double) nd.getNodeData().getAttributes().getValue("longitude");
			Double lat = (Double) nd.getNodeData().getAttributes().getValue("latitude");
			nodeLocations.set(i-1, 0, lon);
			nodeLocations.set(i-1, 1, lat);
			
			nodeLocationsMap.put(new MultiKey(lon,lat), nd);
			
		}//for 
		//Loop through the ports,adding each in turn
		
		for(int i=0;i<list.size();i++){
			System.out.println("Getting nearest node to port " + i + " of " + (list.size()-1));
			
			Port p = list.get(i);
			
			//Create the node
			Node n0 = graphModel.factory().newNode("port_" + i);
	        n0.getNodeData().setLabel(p.getName());
	        n0.getNodeData().getAttributes().setValue("longitude", p.getLon());
	        n0.getNodeData().getAttributes().setValue("latitude", p.getLat());
	        
	        
	        //Create copy of our nodes distances
	        DoubleMatrix2D nodesMatrix = (DoubleMatrix2D) nodeLocations.clone();
	        //Create matrix same size as nodes, but just with the port lon and lat
	        
	        DoubleFactory1D df1 = DoubleFactory1D.dense;
	        DoubleMatrix1D portLon = df1.make( nodeLocations.rows(),p.getLon());
	        DoubleMatrix1D portLat = df1.make(nodeLocations.rows(),p.getLat());
	        
	        
	        //Get the distances
	         double[] distances = Useful.getGreatCircle(portLon.toArray(),portLat.toArray(),ColtUtils.getcol(nodesMatrix, 0).toArray(), ColtUtils.getcol(nodesMatrix, 1).toArray());
	         DoubleMatrix1D vecDists = df1.make(distances);
	         
	         
	        //Now get the minimum
	        double minDist = ColtUtils.getMin(ColtUtils.to2D(vecDists));
	        //and the index of the minimum
	        int closestNode = df1.toList(vecDists).indexOf(minDist);

	        //Now add the edge to this node
	        Node endNode = this.nodeLocationsMap.get(new MultiKey(nodeLocations.get(closestNode, 0),nodeLocations.get(closestNode, 1)));
	        
	        Edge newEdge = graphModel.factory().newEdge(n0, endNode);
	        UndirectedGraph uGraph = graphModel.getUndirectedGraph();
	        uGraph.addNode(n0);
	        uGraph.addEdge(newEdge);
	        
	        //Add this node to port nodes
	        this.portNodes.put(p, n0);
	        
	        //Now add the edge weight
	        newEdge.setWeight((float) minDist);
	        
		}//for i
	}//addPortsToGrid
	
	/**
	 * @brief gets the port to port distances for all ports in the list
	 * 
	 */
	public Map<MultiKey,Double> getPortToPortDistances(ArrayList<Port> ports){

		//Create the holder for our results
		portToPortDistances = new HashMap<MultiKey,Double>();
		this.p_list=ports;
		ListIterator<Port> iter = ports.listIterator();
		while (iter.hasNext()){
			System.out.println("Getting distances for port " + iter.nextIndex() + " of " + (ports.size()-1));
			Port p = iter.next();
			//get the source node
			Node source = portNodes.get(p);
						
			DijkstraShortestPathAlgorithm distAlgo = new DijkstraShortestPathAlgorithm(graphModel.getDirectedGraph(), source);
			//WeightedDijkstra distAlgo = new WeightedDijkstra(graphModel.getUndirectedGraph(), source);
			distAlgo.compute();
			HashMap<NodeData,Double> dists = distAlgo.getDistances();

			//	Loop through our destination nodes and fill in their distances
			for(int i=0;i<this.p_list.size()-1;i++){
				//get the representative node for our destination port
				Port p2 = this.p_list.get(i);
				Node endNode = this.portNodes.get(p2);
				//get the associated distance
				Double dist = dists.get(endNode.getNodeData());
				//add it to our mapping
				portToPortDistances.put(new MultiKey(p,p2), dist);
			}//for i
		}//while iter
		
		return portToPortDistances;
	}
	/**
	 * @brief Writes the port to port distances to database
	 * 
	 * @param db
	 * @param table
	 */
	public void writeDistancesToDB(Boolean createTables,String dbName,String distanceTable,String portTable){
		DatabasePG db = new DatabasePG();
		db.setDb(dbName);
		
		//Create our tables if requested
		if (createTables==Boolean.TRUE){
			
			// Build port table first
			String sql = "CREATE TABLE \"" + portTable + "\"";
			sql =sql + "(";
			sql = sql + "  \"id\" integer NOT NULL,";
			sql = sql + "  \"label\" character varying(255),";
			sql = sql + "  \"lon\" double precision,";
			sql += " \"lat\" double precision,";
			sql = sql + "  \"x\" double precision,";
			sql += " \"y\" double precision,";
			sql += " \"ctry_code\" integer,";
			sql += "\"ctry_name\" character varying(255),";
			sql += "\"un_locode\" character varying(255),";
			sql += "  CONSTRAINT \"primKey_" + portTable + "\" PRIMARY KEY (\"id\"))";
			sql += "WITH (OIDS=FALSE);ALTER TABLE \"" + portTable + "\"  OWNER TO " +this.dbUser + " ;";
			db.executeStatement(sql);
			
			//before doing the distance table, lets create a sequence for incrementing the distance id
			sql = "CREATE SEQUENCE " + distanceTable.toLowerCase() + "_seq_id";
			sql += " INCREMENT 1  MINVALUE 1  MAXVALUE 9223372036854775807  START 24122452  CACHE 1;";
			sql += "ALTER TABLE " + distanceTable.toLowerCase() + "_seq_id  OWNER TO " + this.dbUser + ";";	
			db.executeStatement(sql);
			
			//Now the distanceTable
			sql = "CREATE TABLE \"" + distanceTable + "\"";
			sql =sql + "(";
			sql = sql + "  \"id\" integer NOT NULL DEFAULT nextval('" + distanceTable.toLowerCase() + "_seq_id'::regclass),";
			sql = sql + "  \"source\" integer,";
			sql = sql + "  \"target\" integer,";
			sql += " \"weight\" double precision,";
			sql += "\"distance\" double precision,";
			sql += "  CONSTRAINT \"primKey_" + distanceTable + "\" PRIMARY KEY (\"id\"))";
			sql += "WITH (OIDS=FALSE);ALTER TABLE \"" + distanceTable + "\"  OWNER TO " +this.dbUser + " ;";
			db.executeStatement(sql);
			
		}//if
		
		//Lets write the ports first
		ArrayList<ArrayList<Object>> vals = new ArrayList<ArrayList<Object>>();
		for(int i=0;i<this.p_list.size();i++){
			ArrayList<Object> row = new ArrayList<Object>();
			Port port = this.p_list.get(i);
			//add the id
			row.add(i);
			//add the label
			row.add(port.getName());
			//add the lon
			row.add(port.getLon());
			//add the lat
			row.add(port.getLat());
			//add the x
			row.add(port.getLon());
			//add the y
			row.add(port.getLat());
			//add the country code
			if (port.getParentCountry()!=null){
				row.add(port.getParentCountry().getUncode());
				//add the country code
				row.add(port.getParentCountry().getName());
				//add the locode
				row.add(port.getISO());
			}else{
				row.add(-1);
				row.add("");
				row.add("");
			}
			
			//Add to the arraylist
			vals.add(row);
		}//for i
		
		ArrayList<String> fields = new ArrayList<String>();
		fields.add("id");
		fields.add("label");
		fields.add("lon");
		fields.add("lat");
		fields.add("x");
		fields.add("y");
		fields.add("ctry_code");
		fields.add("ctry_name");
		fields.add("un_locode");
		
		//write to db
		db.runBlockInserts(fields, portTable, vals, 1000);
		
		//Now insert the edges/distances
		//Lets write the ports first
		vals = new ArrayList<ArrayList<Object>>();
		for(int i=0;i<this.p_list.size();i++){
			for (int j=0;j<this.p_list.size();j++){
				Port home = p_list.get(i);
				Port dest = p_list.get(j);
				if (!home.equals(dest)){
					Double dist = this.portToPortDistances.get(new MultiKey(home,dest));
					if (dist!=null){
						if (dist!=Double.POSITIVE_INFINITY){
						
							//Start building our input data
							ArrayList<Object> row = new ArrayList<Object>();
							//Source and target ids should correspond to the index of the ports within port list		
							row.add(i);
							row.add(j);
							//weight and distance
							row.add(dist);
							row.add(dist);
							
							//Add to the arraylist
							vals.add(row);
						}//if
					}//if
				}//if
			}//for j
		}//for i
		
		//Now the fields
		fields = new ArrayList<String>();
		fields.add("source");
		fields.add("target");
		fields.add("weight");
		fields.add("distance");
		
		//write to db
		db.runBlockInserts(fields, distanceTable, vals, 1000);
	}//writeDistancesToDB
	
	/**
	 * @brief Outputs the current graphModel to gephi
	 * 
	 */
	public void writeGraphToGephi(String fileLoc){
        Workspace workspace = pc.getCurrentWorkspace();

        //Get controllers and models
        ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        
		//Export full graph
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
            ec.exportFile(new File(fileLoc));
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
	}
	/**
	 * @return the dbUser
	 */
	public String getDbUser() {
		return dbUser;
	}
	/**
	 * @param dbUser the dbUser to set
	 */
	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}//
	/**
	 * @return the dbPass
	 */
	public String getDbPass() {
		return dbPass;
	}
	/**
	 * @param dbPass the dbPass to set
	 */
	public void setDbPass(String dbPass) {
		this.dbPass = dbPass;
	}
}
