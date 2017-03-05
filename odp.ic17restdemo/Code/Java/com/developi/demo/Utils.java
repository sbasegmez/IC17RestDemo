package com.developi.demo;

public class Utils {

	/**
	 * 	 recycles multiple domino objects (thx Nathan T. Freeman)
	 *		
	 * @param objs
	 * 
	 */
	public static void recycleObjects(lotus.domino.Base... objs) {
		for ( lotus.domino.Base obj : objs ) {
			if (obj != null) {
				try {
					obj.recycle();
				} catch (Exception e) {}
			}
		}
	}

}
