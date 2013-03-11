package eok.modelling;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections.keyvalue.MultiKey;

import eok.generics.Pair;
import eok.location.Country;
import eok.location.CountryFactory;
import eok.location.Port;
import eok.location.PortFactory;

public class getDistances {
	
	//Map<Pair<Port,Port>,Double> distances;
	
	public static void main(String[] args) {
		getDistances.getJBPortDistances();
		
	}//main
	
	public static void getRepPortDistances(){
int resolution = 1;
		
		BuildPortDistances builder = new BuildPortDistances();
		// Get the db details from the properties file
		//load a properties file
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("config.properties"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//get all countries
		String dbUser = prop.getProperty("dbuser");
		String dbPass = prop.getProperty("dbpassword");
		ArrayList<Country> ctries = CountryFactory.getAllCountries( dbUser,dbPass);
		//get each countries representative ports
		ArrayList<Port> ports = new ArrayList<Port>();
		//get our ports
		Iterator<Country> iter = ctries.iterator();
		while(iter.hasNext()){
			Country ctry = iter.next();
			System.out.println("Getting ports for " +ctry.getName());
			Port repPort = ctry.getRepPort();
			if (repPort != null){
				ports.add(repPort);
			}//if
		}//while
		
		//get the baseline grid
		builder.setDbUser(dbUser);
		builder.setDbPass(dbPass);
		builder.buildBaseGrid("sp_nodes_1deg_incl_suez","sp_edges_1deg_incl_suez");
		
		//Now add the new ports to the grid
		builder.addPortsToGrid(ports);
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
		Calendar cal = Calendar.getInstance();
		
		//output to gexf file
		builder.writeGraphToGephi("shortest_path_suez_res" + resolution + "_" + dateFormat.format(cal.getTime()) + ".gexf");
		
		//get the port to port distances
		Map<MultiKey,Double> distances = builder.getPortToPortDistances(ports);
		
		//Now write these to the database
		builder.writeDistancesToDB(Boolean.TRUE,"Gephi", "SuezDistances_res_" + resolution + "_" + dateFormat.format(cal.getTime()),
				"SuezDistancesPorts_res_" + resolution + "_" + dateFormat.format(cal.getTime()));
		
		
		System.out.println("Done!");
	}
public static void getJBPortDistances(){
	int resolution = 1;
	
	BuildPortDistances builder = new BuildPortDistances();
	// Get the db details from the properties file
	//load a properties file
	Properties prop = new Properties();
	try {
		prop.load(new FileInputStream("config.properties"));
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	//get all countries
	String dbUser = prop.getProperty("dbuser");
	String dbPass = prop.getProperty("dbpassword");
	ArrayList<Port> ports = PortFactory.getJBPorts(dbUser, dbPass);
	
	//get the baseline grid
	builder.setDbUser(dbUser);
	builder.setDbPass(dbPass);
	builder.buildBaseGrid("sp_nodes_1deg_incl_suez","sp_edges_1deg_incl_suez");
	
	//Now add the new ports to the grid
	ArrayList<Port> slimmedPorts = new ArrayList<Port>(ports.subList(0, 300));
	builder.addPortsToGrid(slimmedPorts);
	
	DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
	Calendar cal = Calendar.getInstance();
	
	//output to gexf file
	builder.writeGraphToGephi("shortest_path_suez_res" + resolution + "_" + dateFormat.format(cal.getTime()) + ".gexf");
	
	//get the port to port distances
	Map<MultiKey,Double> distances = builder.getPortToPortDistances(slimmedPorts);
	
	//Now write these to the database
	builder.writeDistancesToDB(Boolean.TRUE,"Gephi", "JBSuezDistances_res_" + resolution + "_" + dateFormat.format(cal.getTime()),
			"JBSuezDistancesPorts_res_" + resolution + "_" + dateFormat.format(cal.getTime()));
	
	
	System.out.println("Done!");
}
}
