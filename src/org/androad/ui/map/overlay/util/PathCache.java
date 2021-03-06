// Created by plusminus on 17:57:05 - 21.10.2008
package org.androad.ui.map.overlay.util;

import java.util.ArrayList;
import java.util.List;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;

import org.androad.sys.ors.adt.rs.Route;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Point;


public class PathCache {

	// ===========================================================
	// Constants
	// ===========================================================

	public static final int PATHSEGMENT_SIZE = 100;
	public static final int PATH_ZOOMLEVEL = 16;

	// ===========================================================
	// Fields
	// ===========================================================

	private final ArrayList<Path> mSegments = new ArrayList<Path>();

	private GeoPoint mOrigCenteredStartGeoPoint;

	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public static int getSegmentForIndex(final int aIndex){
		return aIndex / PATHSEGMENT_SIZE;
	}

	public Path getSegment(final int i) {
		return this.mSegments.get(i);
	}

	public ArrayList<Path> getSegments() {
		return this.mSegments;
	}

	public void init(final MapView aMapview, final Route aRoute) {
		this.mOrigCenteredStartGeoPoint = aRoute.getStart();
		final Projection pj = aMapview.getProjection();


		if(aRoute == null) {
			throw new AssertionError();
		}

		this.mSegments.clear();

		final List<GeoPoint> mPolyline = aRoute.getPolyLine();

		final Point reuse = new Point();
		Path cur = null;

		final int size = mPolyline.size();
		for (int i = 0; i < size; i++) {
			final GeoPoint gp = mPolyline.get(i);
			pj.toMapPixels(gp, reuse);

			if(i % PATHSEGMENT_SIZE == 0){
				cur = new Path();
				this.mSegments.add(cur);
				cur.moveTo(reuse.x, reuse.y);
			}else{
				cur.lineTo(reuse.x, reuse.y);
			}
		}
	}

	public Matrix getTransformationMatrix(final Projection curProjection, final GeoPoint pCurrentCenter, final int aZoom) {
		final Matrix out = new Matrix();

		final Point pOrig = curProjection.toMapPixels(this.mOrigCenteredStartGeoPoint, null);
		final Point pNew = curProjection.toMapPixels(pCurrentCenter, null);

		final float dx = pNew.x - pOrig.x;
		final float dy = pNew.y - pOrig.y;

		final float scale = (float)Math.pow(2, aZoom - PATH_ZOOMLEVEL);
		out.postTranslate(dx, dy);
		out.postScale(scale, scale);

		return out;
	}

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
