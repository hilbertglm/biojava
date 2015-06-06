package org.biojava.nbio.structure.io;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Element;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.GroupType;
import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.io.mmcif.SimpleMMcifParser;
import org.biojava.nbio.structure.io.mmcif.model.AtomSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some tools for mmCIF file writing.
 * 
 * See http://www.iucr.org/__data/assets/pdf_file/0019/22618/cifguide.pdf
 * 
 * 
 * @author duarte_j
 *
 */
public class MMCIFFileTools {

	private static final Logger logger = LoggerFactory.getLogger(MMCIFFileTools.class);
	
	private static final String newline = System.getProperty("line.separator");
	
	/**
	 * The character to be printed out in cases where a value is not assigned in mmCIF files
	 */
	public static final String MMCIF_MISSING_VALUE = "?";
	
	/**
	 * The character to be printed out as a default value in mmCIF files, e.g. for the default alt_locs
	 */
	public static final String MMCIF_DEFAULT_VALUE = ".";
	
	/**
	 * The header appearing at the beginning of a mmCIF file. A "block code" can be added to it of no more than 32 chars.
	 */
	public static final String MMCIF_TOP_HEADER = "data_";
	
	/**
	 * Produces a mmCIF loop header string for the given categoryName and className.
	 * className must be one of the beans in the {@link org.biojava.nbio.structure.io.mmcif.model} package
	 * @param categoryName
	 * @param className
	 * @return
	 * @throws ClassNotFoundException if the given className can not be found
	 */
	public static String toLoopMmCifHeaderString(String categoryName, String className) throws ClassNotFoundException {
		StringBuilder str = new StringBuilder();
		
		str.append(SimpleMMcifParser.LOOP_START+newline);
		
		Class<?> c = Class.forName(className);
		
		for (Field f : c.getDeclaredFields()) {
			str.append(categoryName+"."+f.getName()+newline);
		}
		
		return str.toString();
	}
	
	/**
	 * Converts a list of mmCIF beans (see {@link org.biojava.nbio.structure.io.mmcif.model} to
	 * a String representing them in mmCIF loop format with one record per line.
	 * @param list
	 * @return
	 */
	public static String toMMCIF(List<Object> list) {
		int[] sizes = getFieldSizes(list);
		
		StringBuilder sb = new StringBuilder();
		
		for (Object o:list) {
			sb.append(toSingleLineMmCifString(o, sizes));
		}
		
		sb.append(SimpleMMcifParser.LOOP_END+newline);
		
		return sb.toString();
	}
	
	/**
	 * Given a mmCIF bean produces a String representing it in mmCIF loop format as a single record line
	 * @param a
	 * @param sizes the size of each of the fields
	 * @return
	 */
	public static String toSingleLineMmCifString(Object a, int[] sizes) {
		
		StringBuilder str = new StringBuilder();
		
		Class<?> c = a.getClass();
		
		if (sizes.length!=c.getDeclaredFields().length) 
			throw new IllegalArgumentException("The given sizes of fields differ from the number of declared fields");
		
		int i = -1;
		for (Field f : c.getDeclaredFields()) {
			i++;
			f.setAccessible(true);

			try {
				Object obj = f.get(a);
				String val;
				if (obj==null) {
					logger.debug("Field {} is null, will write it out as {}",f.getName(),MMCIF_MISSING_VALUE);
					val = MMCIF_MISSING_VALUE;
				} else {
					val = (String) obj;
				}
				
				str.append(String.format("%-"+sizes[i]+"s ", addMmCifQuoting(val)));
								
				
			} catch (IllegalAccessException e) {
				logger.warn("Field {} is inaccessible", f.getName());
				continue;
			} catch (ClassCastException e) {
				logger.warn("Could not cast value to String for field {}",f.getName());
				continue;
			}
		}
		
		str.append(newline);
		
		return str.toString();
		
	}
	
	/**
	 * Adds quoting to a String according to the STAR format (mmCIF) rules
	 * @param val
	 * @return
	 */
	private static String addMmCifQuoting(String val) {
		String newval;
		
		if (val.contains("'")) {
			// double quoting for strings containing single quotes
			newval = "\""+val+"\"";
		} else if (val.contains(" ")) {
			// single quoting for stings containing spaces
			newval = "'"+val+"'";
		} else {
			if (val.contains(" ") && val.contains("'")) {
				// TODO deal with this case
				logger.warn("Value contains both spaces and single quotes, won't format it: {}",val);
			}
			newval = val;
		}
		// TODO deal with all the other cases: e.g. multi-line quoting
		
		return newval;
	}
	
	/**
	 * Converts an Atom object to an {@link AtomSite} object.
	 * @param a
	 * @param model
	 * @param chainId
	 * @param internalChainId
	 * @return
	 */
	private static AtomSite atomToAtomSite(Atom a, int model, String chainId, String internalChainId) {
		
		/*
		ATOM 7    C CD  . GLU A 1 24  ? -10.109 15.374 38.853 1.00 50.05 ? ? ? ? ? ? 24  GLU A CD  1 
		ATOM 8    O OE1 . GLU A 1 24  ? -9.659  14.764 37.849 1.00 49.80 ? ? ? ? ? ? 24  GLU A OE1 1 
		ATOM 9    O OE2 . GLU A 1 24  ? -11.259 15.171 39.310 1.00 50.51 ? ? ? ? ? ? 24  GLU A OE2 1 
		ATOM 10   N N   . LEU A 1 25  ? -5.907  18.743 37.412 1.00 41.55 ? ? ? ? ? ? 25  LEU A N   1 
		ATOM 11   C CA  . LEU A 1 25  ? -5.168  19.939 37.026 1.00 37.55 ? ? ? ? ? ? 25  LEU A CA  1 		
		*/
		
		Group g = a.getGroup();

		String record ;
		if ( g.getType().equals(GroupType.HETATM) ) {
			record = "HETATM";
		} else {
			record = "ATOM";
		}

		String entityId = "0";
		String labelSeqId = Integer.toString(g.getResidueNumber().getSeqNum());
		if (g.getChain()!=null && g.getChain().getCompound()!=null) {
			entityId = Integer.toString(g.getChain().getCompound().getMolId());
			labelSeqId = Integer.toString(g.getChain().getCompound().getAlignedResIndex(g, g.getChain()));
		}
		
		Character  altLoc = a.getAltLoc()           ;
		String altLocStr = altLoc.toString();
		if (altLoc==null || altLoc == ' ') {
			altLocStr = MMCIF_DEFAULT_VALUE;
		}		
		
		Element e = a.getElement();
		String eString = e.toString().toUpperCase();
		if ( e.equals(Element.R)) {
			eString = "X";
		}
		
		String insCode = MMCIF_MISSING_VALUE;
		if (g.getResidueNumber().getInsCode()!=null ) {
			insCode = Integer.toString(g.getResidueNumber().getInsCode());
		}
		
		AtomSite atomSite = new AtomSite();
		atomSite.setGroup_PDB(record);
		atomSite.setId(Integer.toString(a.getPDBserial()));
		atomSite.setType_symbol(eString);
		atomSite.setLabel_atom_id(a.getName());
		atomSite.setLabel_alt_id(altLocStr);
		atomSite.setLabel_comp_id(g.getPDBName());
		atomSite.setLabel_asym_id(internalChainId);
		atomSite.setLabel_entity_id(entityId);
		atomSite.setLabel_seq_id(labelSeqId);
		atomSite.setPdbx_PDB_ins_code(insCode);
		atomSite.setCartn_x(FileConvert.d3.format(a.getX()));
		atomSite.setCartn_y(FileConvert.d3.format(a.getY()));
		atomSite.setCartn_z(FileConvert.d3.format(a.getZ()));
		atomSite.setOccupancy(FileConvert.d2.format(a.getOccupancy()));
		atomSite.setB_iso_or_equiv(FileConvert.d2.format(a.getTempFactor()));
		atomSite.setAuth_seq_id(Integer.toString(g.getResidueNumber().getSeqNum()));
		atomSite.setAuth_comp_id(g.getPDBName());
		atomSite.setAuth_asym_id(chainId);
		atomSite.setAuth_atom_id(a.getName());
		atomSite.setPdbx_PDB_model_num(Integer.toString(model));
		
		return atomSite;
	}
	
	/**
	 * Converts a Group into a List of {@link AtomSite} objects
	 * @param g
	 * @param model
	 * @param chainId
	 * @param internalChainId
	 * @return
	 */
	private static List<AtomSite> groupToAtomSites(Group g, int model, String chainId, String internalChainId) {
		
		List<AtomSite> list = new ArrayList<AtomSite>();
		
		int groupsize  = g.size();

		for ( int atompos = 0 ; atompos < groupsize; atompos++) {
			Atom a = null ;
			
			a = g.getAtom(atompos);
			if ( a == null)
				continue ;

			list.add(atomToAtomSite(a, model, chainId, internalChainId));
			
		}
		if ( g.hasAltLoc()){
			for (Group alt : g.getAltLocs() ) {
				list.addAll(groupToAtomSites(alt, model, chainId, internalChainId));
			}
		}
		return list;
	}
	
	/**
	 * Converts a Chain into a List of {@link AtomSite} objects
	 * @param c
	 * @param model
	 * @param chainId
	 * @param internalChainId
	 * @return
	 */
	public static List<AtomSite> chainToAtomSites(Chain c, int model, String chainId, String internalChainId) {
		
		List<AtomSite> list = new ArrayList<AtomSite>();
		
		if (c.getCompound()==null) {
			logger.warn("No Compound (entity) found for chain {}: entity_id will be set to 0, label_seq_id will be the same as auth_seq_id", c.getChainID());
		}
	
		for ( int h=0; h<c.getAtomLength();h++){

			Group g= c.getAtomGroup(h);

			list.addAll(groupToAtomSites(g, model, chainId, internalChainId));			
			
		}
		
		return list;
	}
	
	/**
	 * Converts a Structure into a List of {@link AtomSite} objects
	 * @param s
	 * @return
	 */
	public static List<AtomSite> structureToAtomSites(Structure s) {
		List<AtomSite> list = new ArrayList<AtomSite>();
		
		for (int m=0;m<s.nrModels();m++) {
			for (Chain c:s.getChains()) {
				list.addAll(chainToAtomSites(c, m+1, c.getChainID(), c.getInternalChainID()));
			}
		}
		return list;
	}
	
	/**
	 * Finds the max length of each of the String values contained in each of the fields of the given list of beans.
	 * Useful for producing mmCIF loop data that is aligned for all columns.
	 * @param a
	 * @return
	 * @see #toMMCIF(List)
	 */
	private static int[] getFieldSizes(List<Object> list) {
		
		if (list.isEmpty()) throw new IllegalArgumentException("List of beans is empty!");
		
		int[] sizes = new int [list.get(0).getClass().getDeclaredFields().length];
		
		
		for (Object a:list) {
			Class<?> c = a.getClass();

			int i = -1;
			for (Field f : c.getDeclaredFields()) {
				i++;

				f.setAccessible(true);

				try {
					Object obj = f.get(a);
					int length;
					if (obj==null) {
						length = MMCIF_MISSING_VALUE.length();
					} else {
						String val = (String) obj;
						length = addMmCifQuoting(val).length();
					}
					
					if (length>sizes[i]) sizes[i] = length; 			

				} catch (IllegalAccessException e) {
					logger.warn("Field {} is inaccessible", f.getName());
					continue;
				} catch (ClassCastException e) {
					logger.warn("Could not cast value to String for field {}",f.getName());
					continue;
				}
			}
		}
		return sizes;
	}
}
