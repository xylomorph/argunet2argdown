import com.db4o.*
import com.db4o.ext.ExtObjectContainer
import com.db4o.ObjectSet

import org.argunet.model.debate.*
import org.argunet.structures.MapToSet
import org.argunet.model.*
import org.argunet.model.roles.*


import java.util.logging.Logger
import java.util.logging.FileHandler
import java.util.logging.ConsoleHandler
import java.util.logging.SimpleFormatter
import java.util.logging.Level

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import java.util.HashMap


// Configure logging
Logger log = Logger.getLogger("argunet2argdown")
def FileHandler fileHandler = new FileHandler(new File(System.getProperty("user.dir"), "argunet2argdown.log").absolutePath, 10000000 ,5)
log.addHandler(fileHandler)
def SimpleFormatter formatter = new SimpleFormatter()
fileHandler.setFormatter(formatter)

//log.addHandler(new ConsoleHandler())

if(args == null || args.size() == 0){
	throw new Exception("You must provide an Argunet file to the groovy script! (Of the form: groovy -cp LIBRARY_PATH PATH_TO_SCRIPT/argunet2argdown.groovy ARGUNET_FILE). ")
}

argunetFile = new File(args[0])

HashMap<Long,DataObject> nodeContentMap = null
HashMap<Long, Proposition> propositions = null
PropositionSemantics propositionSemantics = null
ArgumentSemantics argumentSemantics = null
MapToSet supportRelations = null
MapToSet attackRelations = null
Set<Long> multiAppearingPropositions = new HashSet<Long>()

if(argunetFile.exists()) {
	log.info("Converting file " + argunetFile.name)
	// unpacking argunet file
	File dataFile = null
	try{
		ZipFile zipFile = new ZipFile(argunetFile)
		if(zipFile.size()!=2)
			throw new Exception("File is not a valid Argunet-File!")     	

		FileInputStream fin = new FileInputStream(argunetFile)
		ZipInputStream zin = new ZipInputStream(fin)
		ZipEntry ze= null
		FileOutputStream fout=null
		byte[] buffer = new byte[1024]
		int length
		while ((ze = zin.getNextEntry()) != null) { 
			
			if(ze.getName().endsWith("yap")){
				dataFile=new File(System.getProperty("user.dir"), "db4o_tmp_file.yap")
				log.info("Extract db4o file to temporary file: " + dataFile.path)
				fout= new FileOutputStream(dataFile) 
				while ((length = zin.read(buffer))>0) {
					fout.write(buffer, 0, length)
				}
				fout.close()
			}
			zin.closeEntry()
		}
		zin.close()
	} catch(IOException e){
		log.error("Could not extract Arg-File!", e);
	}

	db4oFile = dataFile
	ExtObjectContainer dbA = Db4o.openFile(db4oFile.canonicalPath).ext();
	try {
		// Getting ArgumentationSemantics (i.e., sketched relations)
		ObjectSet result = dbA.query(ArgumentSemantics.class)
		if(result.size() == 1) {
			argumentSemantics = (ArgumentSemantics) result.next()
			supportRelations = argumentSemantics.getSupportRelations()
			attackRelations = argumentSemantics.getAttackRelations()
		}
		else 
			throw new Exception("Found " + result.size() + " instances of ArgumentSemantics (instead of 1).")
		// Getting all propositions
		result = dbA.query(Proposition.class)
		if(result.size() >=0) {
			log.info("Found " + result.size()+ " propositions.")
			propositions = new HashMap<Long, Proposition>()
			while(result.hasNext()) {
				prop = (Proposition) result.next()
				propositions.put(prop.getUid(), prop)
			}
		}
		else {
			log.info("Did not find any propositions.")
		}
			
			
		// Getting PropositionsSemantics (i.e., relations)
		result = dbA.query(PropositionSemantics.class)
		if(result.size() == 1) 
			propositionSemantics = (PropositionSemantics) result.next()
		else
			throw new Exception("Found " + result.size() + " instances of PropositionSemantics (instead of 1).")
	
		// Getting all NodeContents 
		result = dbA.query(INodeContent.class)
		if(result.size() >=0) {
			log.info("Found " + result.size()+ " nodes.")
			nodeContentMap = new HashMap<Long,DataObject>()
			while(result.hasNext()) {
				nodeContent = (DataObject) result.next()
				nodeContentMap.put(nodeContent.getUid(), nodeContent)
			}
		}
		else {
			throw new Exception("Did not find any Nodes.")
		}
		
		// loop through Graphs
		result = dbA.query(Graph2.class);
		log.info("Found " + result.size() + " graphs in the argunet file." )
		if(result.size() == 0) 
			throw new Exception("Did not find any argument maps in the file.")
		int graph_i = 1
		while(result.hasNext()) {
			Graph2 graph = (Graph2) result.next()
			// check for each proposition if it used in more than one argument 
			// (this is used later to decide if the proposition will be defined with a title)
			for(Long propUid: propositions.keySet()){
				int counter = 0
				for(Node node: graph.nodes.values()){
					INodeContent nodeContent = nodeContentMap.get(node.getContentId())
					if(nodeContent instanceof Argument){
						Argument argument = (Argument) nodeContent
						for(Role propRole:argument.getPropositions()){
							if(propRole.getPropositionId() == propUid)
								counter += 1
						}
					}
					if(counter > 1){
						multiAppearingPropositions.add(propUid)
						break
					}
				}
			}
			// creating argdown file
			argdownFileName = argunetFile.name.substring(0,argunetFile.name.lastIndexOf("."))
			File argdownFile = new File(System.getProperty("user.dir"), argdownFileName + "_" + 
				graph_i+ ".argdown")
			log.info("Creating argdown file:" + argdownFile.absolutePath)
			
			argdownFile.write "===\n"
			argdownFile << "title: " + graph.getTitle() + "\n"
			argdownFile << "model:\n"
			argdownFile << "    mode: strict\n"
			argdownFile << "===\n\n"
			// looping through Nodes
			for(Node node: graph.nodes.values()){
				//log.info("Node with contentId " + node.getContentId())
				if(nodeContentMap.containsKey(node.getContentId())) {
					INodeContent nodeContent = nodeContentMap.get(node.getContentId())
					argdownFile << nodeContent2Argdown(nodeContent) + "\n"
					
					// adding sketched relations
					if(supportRelations.containsKey(nodeContent.getUid())) {
						for(long uid: supportRelations.get(nodeContent.getUid())) {
							target = nodeContentMap.get(uid)
							argdownFile << "   +> " + nodeContent2Argdown(target) + "\n" 
						}
					}
					if(attackRelations.containsKey(nodeContent.getUid())) {
						for(long uid: attackRelations.get(nodeContent.getUid())) {
							target = nodeContentMap.get(uid)
							argdownFile << "   -> " + nodeContent2Argdown(target) + "\n"
						}
					}
					if(nodeContent instanceof Argument){
						argdownFile << "\n"
						// add reconstruction
						Argument argument = (Argument) nodeContent
						p_i = 1
						for(Role propRole:argument.getPropositions()){
							// if the proposition is a target of an attack or support relation 
							// or if the proposition is also in a thesis node, 
							// then the proposition is given its title
							title = ""
							if(propositionSemantics.getEquivalentPropositions(propRole.getPropositionId()).size()>1 || 
							   propositionSemantics.getContradictingPropositions(propRole.getPropositionId()).size() > 0 || 
							   graph.nodes.containsKey(propRole.getPropositionId()) || 
							   multiAppearingPropositions.contains(propRole.getPropositionId())
							   )
								title = "["+nodeTitle(propositions.get(propRole.getPropositionId())) +"]: "
							
							if(propRole instanceof Conclusion){
								argdownFile << "----\n"
								argdownFile << "("+p_i+") " + title + propositions.get(propRole.getPropositionId()).getContent()
							}
							else if(propRole instanceof Premise){
								proposition = propositions.get(propRole.getPropositionId())
								argdownFile << "("+p_i+") " + title + proposition.getContent()
								// including incoming relations
								for(Long supportUid: propositionSemantics.getEquivalentPropositions(propRole.getPropositionId())){
									if(supportUid != propRole.getPropositionId()){
										argdownFile << "\n   <+ " + "["+nodeTitle(propositions.get(supportUid)) +"]"
									}
								}
								for(Long attackUid: propositionSemantics.getContradictingPropositions(propRole.getPropositionId())){
									argdownFile << "\n   <- " + "["+nodeTitle(propositions.get(attackUid)) +"]"
									
								}
							}
							else if(propRole instanceof Assumption){
								proposition = propositions.get(propRole.getPropositionId())
								argdownFile << "("+p_i+") " + title + "Assumption:" + proposition.getContent()
							
							}
							else if (propRole instanceof PConclusion){
								argdownFile << "----\n"
								proposition = propositions.get(propRole.getPropositionId())
								argdownFile << "("+p_i+") " + title + proposition.getContent()
							
							}
							argdownFile << "\n"
							p_i += 1
						}
					}

					else if (nodeContent instanceof Proposition) {
						// Check if there are further semantic relations to consider
						// The following cases are not considered so far:
						// (1) The conclusion of an argument is defined as equivalent/contradictory to a statement node 
						//     (i.e., certain incoming semantic relations)
						// Remark: We do not check whether it is a conclusion and just add incoming relations. The above
						// code should guarantee that the corresponding conclusions have a defined title.  
						for(Long supportUid: propositionSemantics.getEquivalentPropositions(nodeContent.getUid())){
							if(supportUid != nodeContent.getUid()){
								argdownFile << "   <+ " + "["+nodeTitle(propositions.get(supportUid)) +"]\n"
							}
						}
						for(Long attackUid: propositionSemantics.getContradictingPropositions(nodeContent.getUid())){
							argdownFile << "   <- " + "["+nodeTitle(propositions.get(attackUid)) +"]\n"
							
						}
					}
					
					argdownFile << "\n"
				}
				else
					log.warning("Did not find NodeContent with id " + node.getContentId())
			}
			graph_i += 1
		}
		log.info("Deleting temporary db4o data file.")
		dataFile.delete()
	}
	finally {
		// closing object container
		log.info("Closing Db4o container.")
		dbA.close()
	}
	
	
	
	
	
}
else {
	log.warning("The specified file " + argunetFile + " does not exist.")
}


def nodeContent2Argdown(INodeContent nodeContent) {
	title = nodeTitle(nodeContent)
	if(nodeContent instanceof Argument){
		Argument argument = (Argument) nodeContent
		description = (argument.description!=null && !argument.description.trim().equals(""))? ": "+argument.description:""
		return "<" + title + ">" + description.replace("\n", "").replace("\r", "")
	}
	else if (nodeContent instanceof Proposition) {
		Proposition proposition = (Proposition) nodeContent
		content = (proposition.content!=null && !proposition.content.trim().equals(""))? ": "+proposition.content:""
		return "[" + title + "]" + content.replace("\n", "").replace("\r", "")
		
	}
}

def nodeTitle(INodeContent nodeContent) {
	title =  nodeContent.title
	if(title==null || title.trim().equals("") || title.equals("Untitled"))
		title = nodeContent.getUid()
	return title
}
