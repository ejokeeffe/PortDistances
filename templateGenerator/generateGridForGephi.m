%> Generates the database tables for use in gephi to generate the shortest
%>path
%>
%> @author Eoin O'Keeffe
%>
%> @version 1.0
%> <br /><i>Version 1.1</i> Changed so that Suez gap is added in shapefile
%> Shapefile is generated in ArcGIS
%>
%> @date 19/02/2013
%> </br><i>Version 1.1</i>: 08/03/2013 
%>
%> @todo Add around the world connections
%> @todo add code to add suez and panama links
%function generateGridForGephi()
edgesTable = 'sp_edges_1deg_incl_suez';
nodesTable = 'sp_nodes_1deg_incl_suez';
shapeFile = '../../../Data/Routing/TemplatesShapesForPortDistance/WinterExtent2013_inclCanals_smooth.shp';
lonSpacing = 2; %in degrees
latSpacing = 1.5; %in degrees
% addSuez = 1;

%generate the grid
gridGen = Grid;
boundingCoords = [-180 -90;180 90];
landareas = shaperead(shapeFile);
% figure
% mapshow(landareas);
nodes = gridGen.generateGrid(boundingCoords(:,1),boundingCoords(:,2),...
    deg2km(lonSpacing),deg2km(latSpacing));
% if addSuez==1
%     %Do this by adding A node for Port Suez (29.9503 N, 32.5569E) to
%     %Port Said (31.2533N, 32.3094E), and joining these nodes to their
%     %adjacent nodes
%     suezNode = [32.5569,39.9503];
%     saidNode = [32.3094,31.2533];
%     %Now add these to nodes
%     nodes = [nodes;suezNode;saidNode];
% end %if
[nodes,~] = gridGen.removePoints(nodes,[],landareas,1);
[~,nodes,adjList] = gridGen.generateEdges(nodes);
% if addSuez ==1
%     %Make sure suez and said are connected
%     Disp('Creating the Suez canal');
%     adjList(end,end-1) = Useful.GreatCircleDistance(nodes(end,2),nodes(end,3),...
%         nodes(end-1,2),nodes(end-1,3));
%     adjList(end-1,end) = Useful.GreatCircleDistance(nodes(end,2),nodes(end,3),...
%         nodes(end-1,2),nodes(end-1,3));
% end %if

%nodes = [[1:size(nodes,1)]',nodes];
%[segments] = gridGen.removeLines(segments,nodes,-1,-1,landareas);
%[~,adjDist] = gridGen.getDistances(nodes,adjList); 
adjDist = adjList;
    %add index of nodes to nodes

adjDist = adjDist(:);
adjList = adjList(:);
nodeConnections = [1:length(adjList)]';


%now write to file
db = DataBasePG;
db.db = 'Gephi';
db.user = Useful.getConfigProperty('dbuser','../config.properties');
db.pass = Useful.getConfigProperty('dbpassword','../config.properties');
%nodes
fields = [{'id'};{'x'};{'y'};{'longitude'};{'latitude'}];
vals = num2cell([nodes,nodes(:,2:3)]);
db.runBlockInserts(fields,nodesTable,vals,1000);
%edges
fields = [{'source'};{'target'};{'weight'}];
assignments = GenProb.IndexToAssignment(nodeConnections(adjList~=0,:),[length(nodes) ...
    length(nodes)]);
vals = num2cell(full([assignments adjList(adjList~=0,:)]));
db.runBlockInserts(fields,edgesTable,vals,3000);


disp('Done');