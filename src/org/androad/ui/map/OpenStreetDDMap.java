//Created by plusminus on 17:13:04 - 12.02.2008
package org.androad.ui.map;

import java.io.IOException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.MapController.AnimationType;
import org.osmdroid.views.overlay.ItemizedOverlayControlView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

import org.androad.R;
import org.androad.adt.AndNavLocation;
import org.androad.adt.UnitSystem;
import org.androad.adt.voice.AudibleTurnCommand;
import org.androad.adt.voice.DirectionVoiceCommandListener;
import org.androad.adt.voice.DistanceVoiceElement;
import org.androad.adt.voice.SimpleAudibleTurnCommand;
import org.androad.adt.voice.TurnVoiceElement;
import org.androad.exc.Exceptor;
import org.androad.nav.Navigator;
import org.androad.nav.OffRouteListener;
import org.androad.nav.WayPointListener;
import org.androad.nav.WaypointOptimizer;
import org.androad.nav.stats.StatisticsManager;
import org.androad.nav.util.NavAlgorithm;
import org.androad.osm.views.overlay.util.DirectionArrowDescriptor;
import org.androad.osm.views.tiles.util.OSMMapTilePreloader;
import org.androad.preferences.PreferenceConstants;
import org.androad.preferences.Preferences;
import org.androad.sound.ISoundManager;
import org.androad.sound.MediaPlayerManager;
import org.androad.sound.tts.SpeechImprover;
import org.androad.sys.ors.adt.aoi.AreaOfInterest;
import org.androad.sys.ors.adt.aoi.CircleByCenterPoint;
import org.androad.sys.ors.adt.lus.Country;
import org.androad.sys.ors.adt.rs.DirectionsLanguage;
import org.androad.sys.ors.adt.rs.Route;
import org.androad.sys.ors.adt.rs.RouteInstruction;
import org.androad.sys.ors.adt.rs.RoutePreferenceType;
import org.androad.sys.ors.aps.APSRequester;
import org.androad.sys.ors.exceptions.ORSException;
import org.androad.sys.ors.rs.RSOfflineLoader;
import org.androad.sys.ors.rs.RouteFactory;
import org.androad.sys.ors.views.overlay.AreaOfInterestOverlay;
import org.androad.ui.common.CommonCallback;
import org.androad.ui.common.CommonDialogFactory;
import org.androad.ui.common.views.RotateView;
import org.androad.ui.map.hud.IHUDImpl;
import org.androad.ui.map.overlay.MapDrivingDirectionsOverlay;
import org.androad.ui.sd.SDMainChoose;
import org.androad.ui.sd.SDPOISearchList;
import org.androad.ui.settings.SettingsMenu;
import org.androad.ui.settings.SettingsRoutingFlags;
import org.androad.ui.util.Util;
import org.androad.util.FileSizeFormatter;
import org.androad.util.UserTask;
import org.androad.util.constants.Constants;
import org.xml.sax.SAXException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager.BadTokenException;
import android.widget.ImageView;
import android.widget.Toast;

public class OpenStreetDDMap extends OpenStreetMapAndNavBaseActivity implements OffRouteListener, DirectionVoiceCommandListener, WayPointListener, PreferenceConstants, Constants {

	private final int TIME_BETWEEN_MISS_REFETCH = 8000;
	private static final int MAPFETCH_TRIES_TO_GET_GPS = 10;
	private static final int MAPFETCH_TRIES_TO_GET_ROUTE = 2;

	/** Time in milliseconds the Autozooming is disabled, after the zoombuttons were used. */
	private static final int AUTOZOOM_BLOCKTIME = 12000;
	/** Time in milliseconds the Autocentering is disabled, after the user panned the map. */
	private static final int AUTOCENTER_BLOCKTIME = 8000;

	private static final boolean INITDRIVINGDIRECTIONS_NEWROUTE = false;
	/** Used when the Route needs to be just restored, after a call to <code>onRestoreInstanceState</code>, which probably was caused by a Configuration-change, like the a change of the Scree-Orientation. */
	private static final boolean INITDRIVINGDIRECTIONS_RESTORE = true;


	private static final String STATE_WAYPOINTS_ID = "state_waypoints_id";
	private static final String STATE_STATICNAVCURRENT_ID = "state_liteversioncurrent_id";
	private static final String STATE_STATICNAVNEXT_ID = "state_liteversionnext_id";
	private static final String STATE_DOAUTOMATICROUTEREFETCH_ID = "state_doautomaticrouterefetch_id";
	private static final String STATE_INITIALROUTEFETCH_ID = "state_initialroutefetch_id";
	private static final String STATE_ROUTE_ID = "state_route_id";
	private static final String STATE_LASTWORKINGROUTE_ID = "state_lastworkingroute_id";

	// ===========================================================
	// Final Fields
	// ===========================================================

	/* REQUEST-CODES for SubActivities. */
	private static final int REQUESTCODE_SETTINGS = 0x1937;
	private static final int REQUESTCODE_SD_WAYPOINT = REQUESTCODE_SETTINGS + 1;
	private static final int REQUESTCODE_ROUTINGFLAGS = REQUESTCODE_SD_WAYPOINT + 1;

	public static final int OFF_ROUTE = 0;
	public static final int ON_ROUTE = OFF_ROUTE + 1;
	public static final int REFETCHING_ROUTE = ON_ROUTE + 1;

	private static final int MENU_SETTINGS_ID = Menu.FIRST;
	private static final int MENU_REFETCH_ROUTE_ID = MENU_SETTINGS_ID + 1;
	private static final int MENU_QUIT_ID = MENU_REFETCH_ROUTE_ID + 1;
	private static final int MENU_SUBMENU_RENDERERS_ID = MENU_QUIT_ID + 1;
	private static final int MENU_SATELLITE_ID = MENU_SUBMENU_RENDERERS_ID + 1;
	private static final int MENU_TRAFFIC_ID = MENU_SATELLITE_ID + 1;
	private static final int MENU_SUBWAYPOINTS_ID = MENU_TRAFFIC_ID + 1;
	private static final int MENU_WAYPOINT_ADD_ID = MENU_SUBWAYPOINTS_ID + 1;
	private static final int MENU_WAYPOINTS_CLEAR_ID = MENU_WAYPOINT_ADD_ID + 1;
	private static final int MENU_WAYPOINTS_OPTIMIZE_ID = MENU_WAYPOINTS_CLEAR_ID + 1;
	private static final int MENU_PRELOADTILES_ID = MENU_WAYPOINTS_OPTIMIZE_ID + 1;
	private static final int MENU_REVERSEROUTE_ID = MENU_PRELOADTILES_ID + 1;
	private static final int MENU_ROUTEINSTRUCTIONS_ID = MENU_REVERSEROUTE_ID + 1;
	private static final int MENU_ALTITUDEPROFILE_ID = MENU_ROUTEINSTRUCTIONS_ID + 1;
	private static final int MENU_SHAREROUTE_ID = MENU_ALTITUDEPROFILE_ID + 1;
	private static final int MENU_GPSSTATUS_ID = MENU_SHAREROUTE_ID + 1;
	private static final int MENU_ZOOMTODESTINATION_ID = MENU_GPSSTATUS_ID + 1;

	private static final int MENU_SUBMENU_LAYERS_OFFSET = 1000;

	private static final int CONTEXTMENU_ADDASWAYPOINT = Menu.FIRST;
	private static final int CONTEXTMENU_CLEARWAYPOINTS = CONTEXTMENU_ADDASWAYPOINT + 1;
	private static final int CONTEXTMENU_CLOSE = CONTEXTMENU_CLEARWAYPOINTS + 1;

	private static final int DIALOG_SHOW_ALTITUDE_PROFILE = 0;


	// ===========================================================
	// Fields
	// ===========================================================

	private Bitmap mCurrentAltitudeProfileBitmap;

	/** The Destination {@link GeoPoint} of the route. */
	private GeoPoint mGPDestination;
	/** The Start {@link GeoPoint} of the current route. */
	private GeoPoint mGPStart;
	/** The Start {@link GeoPoint} of the very first route. */
	private GeoPoint mGPStartInitial;

	private boolean mDoAutomaticRouteRefetch = true;

	/** Holds the timestamp until the AutoZoom is blocked, because the user has used the Zoom-Buttons. */
	private long mAutoZoomBlockedUntil = 0;
	/** Holds the timestamp until the AutoCentering is blocked, because the user has panned the map. */
	private long mAutoCenterBlockedUntil = 0;

	private TextToSpeech mTTS;
	
	private boolean mTTSAvailable = false;

	private boolean mRealtimeNav = true;

	private int mStaticNavCurrentTurnIndex = Constants.NOT_SET;
	private int mStaticNavNextTurnIndex = Constants.NOT_SET;

	private AreaOfInterestOverlay mAreaOfAvoidingsOverlay;

	private final ArrayList<AreaOfInterest> mAvoidAreas = new ArrayList<AreaOfInterest>();

	private ItemizedOverlayControlView mMapItemControlView;
	private ScaleBarOverlay mScaleIndicatorView;

	/**
	 * Indicates whether direction voice is enabled.
	 * Loaded from Preferences in onResume().
	 */
	private boolean mDirectionVoiceEnabled = false;

	private ISoundManager mSoundManager;

	/**
	 * Indicates whether driving-statistics are generated.
	 * Loaded from Preferences in onResume().
	 */
	private boolean mStatisticsEnabled = false;
	private StatisticsManager mStatisticsManager;

	private HashMap<Integer, Integer> mTurnVoiceSayList;

	private ImageView mIvRouteStatus;
	private Drawable mOffRouteDrawable;
	private Drawable mRouteRefetchDrawable;
	private IHUDImpl mHUDImpl;

	private ArrayList<GeoPoint> mWayPoints = new ArrayList<GeoPoint>();
	private Route mRoute;
	private Route mLastWorkingRoute;
	private ArrayList<GeoPoint> mLastWorkingWayPoints = new ArrayList<GeoPoint>();
	private Navigator mNavigator;

	private RotateView mMapRotateView;

	private MapDrivingDirectionsOverlay mMyMapDrivingDirectionsOverlay = null;

	private ProgressDialog mRouteFetchProgressDialog;
	private Bundle mBundleCreatedWith;
	private GeoPoint mGPLastMapClick;

	private int mCenterMode = PREF_CENTERMODE_DEFAULT;
	private int mRotateMode = PREF_ROTATEMODE_DEFAULT;
	private boolean mSnapToRouteEnabled = PREF_SNAPTOROUTE_DEFAULT;
	private int mSnapToRouteRadius = PREF_SNAPTOROUTE_RADIUS_DEFAULT;
	private boolean mAutoZoomEnabled = PREF_AUTOZOOM_DEFAULT;

	private DirectionsLanguage mDrivingDirectionsLanguage;

	private int mOnRouteStatus = REFETCHING_ROUTE;
	private boolean mRouteRefetchRunning = true;

	/** Got created by the GUI-Thread and there for can create i.e. Toasts. */
	private final Handler mRefetchTriggerHandler = new Handler();

	/** The country this route is being in. */
	private Country mRouteCountry;

	/**
	 * Only true on the very first call of
	 * kickOffDialog/startFecthingDirections. Males the GoogleMaps-Logo
	 * disappear.
	 */
	private boolean mInitialRouteFetch = true;

	private PowerManager.WakeLock mWakeLock;

	private final TextToSpeech.OnInitListener mTTSInitListener = new TextToSpeech.OnInitListener() {
		@Override
		public void onInit(final int version) {
//			OpenStreetDDMap.this.mTTS.setLanguage(Locale.US);
//			OpenStreetDDMap.this.mTTS.setSpeechRate(130);

			initGeneratedVoice();
			OpenStreetDDMap.this.mTTSAvailable = true;
		}
	};

	private final Runnable mRefetchRunner = new Runnable() {

		@Override
		public void run() {
			if (OpenStreetDDMap.this.mDoAutomaticRouteRefetch){
				/* Check if still true (route was not resumed in between). */
				if(OpenStreetDDMap.this.mRouteRefetchRunning) {
					if (OpenStreetDDMap.this.mOnRouteStatus != REFETCHING_ROUTE) {

						if (OpenStreetDDMap.this.mDirectionVoiceEnabled) {
							OpenStreetDDMap.this.mSoundManager.playSound(R.raw.refetching_route);
						}


						OpenStreetDDMap.this.mIvRouteStatus.setImageDrawable(OpenStreetDDMap.this.mRouteRefetchDrawable);
						OpenStreetDDMap.this.mIvRouteStatus.setVisibility(View.VISIBLE);

						Log.d(Constants.DEBUGTAG, "RE-Fetching Route!");

						try {
							/* Start a "REfetching route"-dialog. */
							OpenStreetDDMap.this.kickOffRouteFetch();
						} catch (final BadTokenException bte) {
							/*
							 * Thrown when this ProgressDialog.show() is called and
							 * Activity is already ended. Can happen because of we
							 * are calling postDelayed!
							 */
						}
					}
				}
			}
		}
	};

	// ===========================================================
	// "Constructor"
	// ===========================================================

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		Log.d(Constants.DEBUGTAG, "OnCREATE");

		super.onCreate(savedInstanceState);

		this.mRealtimeNav = Preferences.getRealTimeNav(this);

		/*
		 * Retrieve the Extras Bundle of the Intent this Activity was created
		 * with, because it contains the Information, to be used for retrievign the Route API.
		 */
		this.mBundleCreatedWith = this.getIntent().getExtras();

		this.mRouteCountry = this.mBundleCreatedWith.getParcelable(EXTRAS_COUNTRY_ID);

		this.mTTS = new TextToSpeech(this, this.mTTSInitListener);

		final int searchMode = OpenStreetDDMap.this.mBundleCreatedWith.getInt(EXTRAS_MODE);
		switch(searchMode){
			case EXTRAS_MODE_LOAD_SAVED_ROUTE:
				this.mDoAutomaticRouteRefetch = false;
				break;
			default:
				this.mDoAutomaticRouteRefetch = true;
				break;
		}

		this.mSoundManager = MediaPlayerManager.getInstance(this);

		final int displayQuality = Preferences.getDisplayQuality(this);

		this.mHUDImpl.getRemainingSummaryView().setDisplayQuality(displayQuality);
		this.mHUDImpl.getNextActionView().setDisplayQuality(displayQuality);
		this.mHUDImpl.getUpperNextActionView().setDisplayQuality(displayQuality);

		/* Load the ItemizedControlView. */
		this.mMapItemControlView = (ItemizedOverlayControlView)findViewById(R.id.itemizedoverlaycontrol_ddmap);

		/* This code together with the one in onResume() will make the screen be always on during navigation. */
		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "MyWakeLock");
		this.mWakeLock.acquire();

		/* CleanUp a maybe dead previous statistic session. */
		Preferences.cleanStatisticsSession(this);

		/* Make the system know we want to control the volume on the MUSIC-STREAM with the Hardware-Buttons. */
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

		this.applyViewListeners();
		this.applyMapViewLongPressListener();


		final UnitSystem unitSystem = Preferences.getUnitSystem(this);

		this.mHUDImpl.getNextActionView().setUnitSystem(unitSystem);
		this.mHUDImpl.getUpperNextActionView().setUnitSystem(unitSystem);
		this.mHUDImpl.getRemainingSummaryView().setUnitSystem(unitSystem);

		this.mNavigator = new Navigator(this, unitSystem);
		this.mNavigator.setOffTrackListener(this); // TODO auch zu !REALTIMENAV ?
		this.mNavigator.setWayPointListener(this); // TODO auch zu !REALTIMENAV ?

		// TODO if(searchMode == EXTRAS_MODE_LOAD_SAVED_ROUTE) show only realtime/static option.
		if(savedInstanceState != null){
			// Nothing (initDrivingDirections gets called through onRestoreInstanceState)
		}else if(Preferences.getNavSettingsRemember(this) || searchMode == EXTRAS_MODE_LOAD_SAVED_ROUTE){
			initDrivingDirections(INITDRIVINGDIRECTIONS_NEWROUTE);
		}else{
			final Intent getFlagsIntent = new Intent(this, SettingsRoutingFlags.class);
			final Bundle b = new Bundle();
			b.putBoolean(SettingsRoutingFlags.IS_DIALOG_MODE, true);
			getFlagsIntent.putExtras(b);
			startActivityForResult(getFlagsIntent, REQUESTCODE_ROUTINGFLAGS);
		}
	}

	@Override
	protected void onSetupContentView() {
		this.mHUDImpl = Preferences.getHUDImpl(this);
		final int variationID = Preferences.getHUDImplVariationID(this);
		this.setContentView(this.mHUDImpl.getVariation(variationID).getLayoutID());
		this.mHUDImpl.init(this.findViewById(R.id.ddmap_root));

		super.mOSMapView = (MapView) findViewById(R.id.map_ddmap);
		super.mOSMapView.setTileSource(Preferences.getMapViewProviderInfoDDMap(this));

		this.mMapRotateView = (RotateView) findViewById(R.id.rotator_ddmap);

		this.mIvRouteStatus = (ImageView) findViewById(R.id.iv_ddmap_offroute);
		final Resources resources = getResources();
		this.mOffRouteDrawable = resources.getDrawable(R.drawable.route_missed);
		this.mRouteRefetchDrawable = resources.getDrawable(R.drawable.route_refetch);

		final int displayQuality = Preferences.getDisplayQuality(this);

		final OverlayManager overlaymanager = this.mOSMapView.getOverlayManager();

		this.mScaleIndicatorView = new ScaleBarOverlay(this);
        if (Preferences.getUnitSystem(this) == UnitSystem.IMPERIAL) {
            this.mScaleIndicatorView.setImperial();
        } else {
            this.mScaleIndicatorView.setMetric();
        }
        this.mScaleIndicatorView.setScaleBarOffset(getResources().getDisplayMetrics().widthPixels/2 - getResources().getDisplayMetrics().xdpi/2, 10);
        overlaymanager.add(this.mScaleIndicatorView);

		/* Add a new instance of our fancy Overlay-Class to the MapView. */
		final DirectionArrowDescriptor pDirectionArrowDescriptor = Preferences.getHUDImplVariationDirectionArrowDescriptor(this);
		this.mMyMapDrivingDirectionsOverlay = new MapDrivingDirectionsOverlay(this, displayQuality, this.mRealtimeNav, pDirectionArrowDescriptor);


		/* The AvoidArea-Overlay. */
		this.mAreaOfAvoidingsOverlay = new AreaOfInterestOverlay(this, this.mAvoidAreas);
		overlaymanager.add(this.mMyMapDrivingDirectionsOverlay);
		overlaymanager.add(this.mAreaOfAvoidingsOverlay);
	}

	/**
	 * @see INITDRIVINGDIRECTIONS_NEWROUTE or INITDRIVINGDIRECTIONS_JUSTREFRESH
	 * @param pRestore <code>true</code> to just re-initlalize the objects that working on <code>mRoute</code> like the navigator.<br/>
	 * <code>false</code> will in the end overwrite <code>mRoute</code> and the initialize the objects working on it.
	 */
	private void initDrivingDirections(final boolean pRestore) {
		initStaticNavControlsIfNeccessary();

		/* Start the "fetching route"-dialog. */
		this.mRefetchTriggerHandler.postDelayed(new Runnable() {
			public void run() {
				OpenStreetDDMap.this.kickOffRouteFetch(pRestore);
			}
		}, 500);
	}

	/** Makes the */
	private void initStaticNavControlsIfNeccessary() {
		if(!this.mRealtimeNav){
			/* Initialize the Overlay-Cotrol*/
			this.mMapItemControlView.setVisibility(View.VISIBLE);
			this.mMapItemControlView.setNavToVisible(View.GONE);
			this.mMapItemControlView.setPreviousEnabled(false);
			this.mMapItemControlView.setNextEnabled(false);
			this.mMapItemControlView.setItemizedOverlayControlViewListener(new StaticNavOverlayControlView());
		}
	}

	private void initGeneratedVoice() {
		//		final String pkgName = AndRoadApplication.class.getPackage().getName(); // Root Package!
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	public MapView getMapView() {
		return this.mOSMapView;
	}

	public float getMetersDrivenThisSession() {
		return this.mStatisticsManager.getMetersScaleDrivenSession();
	}

	public Navigator getNavigator() {
		return this.mNavigator;
	}

	public Route getRoute() {
		return this.mRoute;
	}

	/**
	 * Between 1 (far) and 21 (closest).
	 * @return the zoomLevel
	 */
	public int getZoomLevel() {
		return this.mOSMapView.getZoomLevel();
	}

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	@Override
	protected Dialog onCreateDialog(final int id) {
		switch(id){
			case DIALOG_SHOW_ALTITUDE_PROFILE:
				return CommonDialogFactory.createAltitudeProfileDialog(this, new CommonCallback<Void>(){
					@Override
					public void onSuccess(final Void result) {
						if(OpenStreetDDMap.this.mCurrentAltitudeProfileBitmap != null) {
							OpenStreetDDMap.this.mCurrentAltitudeProfileBitmap.recycle();
						}
					}

					@Override
					public void onFailure(final Throwable t) {
						if(OpenStreetDDMap.this.mCurrentAltitudeProfileBitmap != null) {
							OpenStreetDDMap.this.mCurrentAltitudeProfileBitmap.recycle();
						}
					}
				});
			default:
				return null;
		}
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog) {
		switch(id){
			case DIALOG_SHOW_ALTITUDE_PROFILE:
				CommonDialogFactory.prepareAltitudeProfileDialog(this, dialog, this.mCurrentAltitudeProfileBitmap);
				return;
		}
	}

	/**
	 * @return May return the projected location instead of the actual location, depending on <code>mSnapToRouteEnabled</code>.
	 */
	public GeoPoint getLastKnownLocationAsGeoPoint(final boolean pUseRemembered) {
		final GeoPoint actualLocation = super.getLastKnownLocation(pUseRemembered);
		if(actualLocation != null && this.mSnapToRouteEnabled){
			final GeoPoint projectedLocation = this.mNavigator.getLastKnownLocationProjectedGeoPoint();
			if(projectedLocation != null){
				final int differenceOfProjectionToActual = actualLocation.distanceTo(projectedLocation);
				if(differenceOfProjectionToActual < this.mSnapToRouteRadius){
					return projectedLocation;
				}else{
					return actualLocation;
				}
			}else{
				return actualLocation;
			}
		}else{
			return actualLocation;
		}
	}

	@Override
	public void release(){
		this.mRouteRefetchDrawable = null;
		this.mOffRouteDrawable = null;
	}

	@Override
	public void onDataStateChanged(final int strength) {
		// TODO Nothing?
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		switch(keyCode){
			case KeyEvent.KEYCODE_R:
				this.mRouteRefetchRunning = false; // TODO Check if it should be really false
				this.mDoAutomaticRouteRefetch = true;
				kickOffRouteFetch();
				return true;
			case KeyEvent.KEYCODE_A:
				this.mAutoZoomEnabled = !this.mAutoZoomEnabled;
				if(this.mAutoZoomEnabled) {
					Toast.makeText(this, R.string.toast_autozoom_enabled, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(this, R.string.toast_autozoom_disabled, Toast.LENGTH_SHORT).show();
				}
				return true;
			case KeyEvent.KEYCODE_S:
				this.mSnapToRouteEnabled = !this.mSnapToRouteEnabled;
				if(this.mSnapToRouteEnabled) {
					Toast.makeText(this, R.string.toast_snaptoroute_enabled, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(this, R.string.toast_snaptoroute_disabled, Toast.LENGTH_SHORT).show();
				}
				return true;
			case KeyEvent.KEYCODE_SEARCH:
				startWaypointActivity();
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		menu.setQwertyMode(true);
		int menuPos = Menu.FIRST;
		{ // Settings-Item
			menu.add(menuPos, MENU_SETTINGS_ID, menuPos, getString(R.string.maps_menu_settings))
			.setIcon(R.drawable.settings)
			.setAlphabeticShortcut('s');
			menuPos++;
		}

		{ // Renderers-SubMenuItem
			final SubMenu subMenu = menu.addSubMenu(menuPos, MENU_SUBMENU_RENDERERS_ID, menuPos, getString(R.string.maps_menu_submenu_renderers)).setIcon(R.drawable.layers);
			menuPos++;
			{
				final ITileSource[] providers = TileSourceFactory.getTileSources().toArray(new ITileSource[0]);
				for(int j = 0; j < providers.length; j ++){
					final SpannableString itemTitle = new SpannableString(providers[j].name());
					itemTitle.setSpan(new StyleSpan(Typeface.ITALIC), providers[j].name().length(), itemTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					itemTitle.setSpan(new RelativeSizeSpan(0.5f), providers[j].name().length(), itemTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
					subMenu.add(0, MENU_SUBMENU_LAYERS_OFFSET + j, Menu.NONE, itemTitle);
				}
			}
		}

		{ // Waypoints-SubMenuItem
			final SubMenu waypointsSubMenu = menu.addSubMenu(menuPos, MENU_SUBWAYPOINTS_ID, menuPos, getString(R.string.maps_menu_submenu_waypoints)).setIcon(R.drawable.waypoint);
			menuPos++;
			{
				waypointsSubMenu.add(0, MENU_WAYPOINT_ADD_ID, Menu.NONE, getString(R.string.maps_menu_waypoint_add))
				.setIcon(R.drawable.waypoint)
				.setAlphabeticShortcut('a');
				waypointsSubMenu.add(1, MENU_WAYPOINTS_OPTIMIZE_ID, Menu.NONE, getString(R.string.maps_menu_waypoints_optimize))
				.setIcon(R.drawable.optimize)
				.setAlphabeticShortcut('o');
				waypointsSubMenu.add(2, MENU_WAYPOINTS_CLEAR_ID, Menu.NONE, getString(R.string.maps_menu_waypoints_clear))
				.setIcon(R.drawable.wipe)
				.setAlphabeticShortcut('w');
			}
		}

		{ // Refetch-Item
			menu.add(menuPos, MENU_REFETCH_ROUTE_ID, menuPos, getString(R.string.maps_menu_refetch_route))
			.setIcon(R.drawable.route_refetch)
			.setAlphabeticShortcut('r');
			menuPos++;
		}

		{ // Preload-Item
			menu.add(menuPos, MENU_PRELOADTILES_ID, menuPos, getString(R.string.maps_menu_preload))
			.setIcon(R.drawable.preload)
			.setAlphabeticShortcut('p');
			menuPos++;
		}

		{ // ReverseRoute-Item
			menu.add(menuPos, MENU_REVERSEROUTE_ID, menuPos, getString(R.string.maps_menu_reverseroute))
			.setIcon(R.drawable.refresh)
			.setAlphabeticShortcut('a');
			menuPos++;
		}

		{ // ShareRoute-Item
			menu.add(menuPos, MENU_SHAREROUTE_ID, menuPos, getString(R.string.maps_menu_shareroute))
			.setIcon(R.drawable.share_route)
			.setAlphabeticShortcut('m');
			menuPos++;
		}

        { // Route Instructions
            menu.add(menuPos, MENU_ROUTEINSTRUCTIONS_ID, menuPos, getString(R.string.maps_menu_routeinstructions))
			.setIcon(R.drawable.turn_right_90)
			.setAlphabeticShortcut('i');
			menuPos++;
        }

		{ // Altitude Profile-Item
			menu.add(menuPos, MENU_ALTITUDEPROFILE_ID, menuPos, getString(R.string.maps_menu_altitude_profile))
			.setIcon(R.drawable.altitude_profile)
			.setAlphabeticShortcut('h');
			menuPos++;
		}

		{ // GPS-Status-Item
			menu.add(menuPos, MENU_GPSSTATUS_ID, menuPos, getString(R.string.maps_menu_gpsstatus))
			.setIcon(R.drawable.gps_status)
			.setAlphabeticShortcut('g');
			menuPos++;
		}

		{ // GPS-Status-Item
			menu.add(menuPos, MENU_ZOOMTODESTINATION_ID, menuPos, getString(R.string.maps_menu_zoomtodestination))
			.setIcon(R.drawable.zoom_in) // Icon CHECK to big?
			.setAlphabeticShortcut('z');
			menuPos++;
		}

		{
			// TODO Avoid-Area SubMenu, just like WayPoints.
		}

		{ // Close-Item
			if(menu.size() <= 5){ // If there will be no 'more'-item
				menu.add(menuPos, MENU_QUIT_ID, menuPos, getString(R.string.maps_menu_quit))
				.setIcon(R.drawable.exit)
				.setAlphabeticShortcut('q');
			}else{
				// Place it as the fifth.
				menu.add(4, MENU_QUIT_ID, 4, getString(R.string.maps_menu_quit))
				.setIcon(R.drawable.exit)
				.setAlphabeticShortcut('q');
			}
		}

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
		final int itemId = item.getItemId();
		switch(itemId){
			case MENU_REFETCH_ROUTE_ID:
				this.mRouteRefetchRunning = false;  // TODO Check if it should be really false
				this.mDoAutomaticRouteRefetch = true;
				kickOffRouteFetch();
				return true;
			case MENU_SETTINGS_ID:
				final Intent settingsIntent = new Intent(this, SettingsMenu.class);
				startActivityForResult(settingsIntent, REQUESTCODE_SETTINGS);
				return true;
			case MENU_GPSSTATUS_ID:
				org.androad.ui.util.Util.startUnknownActivity(this, "com.eclipsim.gpsstatus.VIEW", "com.eclipsim.gpsstatus");
				return true;
			case MENU_WAYPOINT_ADD_ID:
				startWaypointActivity();
				return true;
			case MENU_PRELOADTILES_ID:
				startPreloadTilesDialog();
				return true;
			case MENU_WAYPOINTS_CLEAR_ID:
				this.mWayPoints.clear();
				this.kickOffRouteFetch();
				return true;
			case MENU_SHAREROUTE_ID:
				handleShareRoute(OpenStreetDDMap.this.mRoute.getRouteHandleID());
				return true;
            case MENU_ROUTEINSTRUCTIONS_ID:
                routeInstructions();
                return true;
			case MENU_ZOOMTODESTINATION_ID:
				zoomToDestination();
				return true;
			case MENU_WAYPOINTS_OPTIMIZE_ID:
				final List<GeoPoint> vias = this.mRoute.getVias();
				final boolean change = WaypointOptimizer.optimize(this.getLastKnownLocationAsGeoPoint(true), vias, this.mRoute.getDestination());
				if (change) {
					this.mWayPoints.clear();
					for (final GeoPoint gp : vias) {
						this.mWayPoints.add(gp);
					}
					this.kickOffRouteFetch();
				} else {
					Toast.makeText(this, "Already optimized", Toast.LENGTH_SHORT).show();
				}
				return true;
			case MENU_ALTITUDEPROFILE_ID:
				showAltitudeProfile();
				return true;
			case MENU_REVERSEROUTE_ID:
				Collections.reverse(this.mWayPoints);
				final GeoPoint tmp = this.mGPStartInitial;
				this.mGPStartInitial = this.mGPDestination;
				this.mGPDestination = tmp;
				this.kickOffRouteFetch(this.mGPStartInitial); // Use the old destination as start
				return true;
			case MENU_QUIT_ID:
				this.setResult(SUBACTIVITY_RESULTCODE_CHAINCLOSE_QUITTED);
				this.finish();
				return true;
			default:
				if(itemId >= MENU_SUBMENU_LAYERS_OFFSET){
					final ITileSource provider = TileSourceFactory.getTileSources().toArray(new ITileSource[0])[item.getItemId() - MENU_SUBMENU_LAYERS_OFFSET];
					/* Check if Auto-Follow has to be disabled. */
                    /* Remember changes to the provider to start the next time with the same provider. */
                    Preferences.saveMapViewProviderInfoDDMap(this, provider);
                    this.mOSMapView.setTileSource(provider);
					return true;
				}
		}

		return super.onMenuItemSelected(featureId, item);
	}

    private void routeInstructions() {
		final Intent intent = new Intent(this, RouteInstructions.class);
		final Bundle b = new Bundle();
		b.putParcelable(RouteInstructions.class.getName(), this.mRoute);
		intent.putExtra(RouteInstructions.class.getName(), b);

		startActivity(intent);
    }

	private void zoomToDestination() {
		this.mAutoZoomBlockedUntil = System.currentTimeMillis() + AUTOZOOM_BLOCKTIME;
		this.mAutoCenterBlockedUntil = System.currentTimeMillis() + AUTOCENTER_BLOCKTIME;

		this.mOSMapView.getController().stopAnimation(false);
		this.mOSMapView.getController().zoomInFixing(this.mRoute.getDestination());
	}

	private void handleShareRoute(final long pRouteHandleID) {
		Util.openEmail(this,
				"Hi,\n\nHave a look at this AndRoad-route:\nRouteHandleID = " + pRouteHandleID + "\n\nSee you =)\n\n\nTo view the route, open AndRoad, then open the Map and type the RouteHandleID from above to the SearchBox.",
				"AndRoad Shared Route",
				null);
	}

	private void showAltitudeProfile() {
		if(this.mRoute == null){

		}else{
			Toast.makeText(OpenStreetDDMap.this, R.string.please_wait_a_moment, Toast.LENGTH_LONG).show();

			new UserTask<Route, Void, Bitmap>(){
				@Override
				public Bitmap doInBackground(final Route... pRoute) {
					try {
						int startIndex = (OpenStreetDDMap.this.mNavigator.isReady()) ? OpenStreetDDMap.this.mNavigator.getNextRoutePointIndex() : 0;
						if(startIndex == Constants.NOT_SET) {
							startIndex = 0;
						}
						return APSRequester.request(pRoute[0].getPolyLine(), startIndex);
					} catch (final Exception e){
						return null;
					}
				}

				@Override
				public void onPostExecute(final Bitmap result) {
					super.onPostExecute(result);
					OpenStreetDDMap.this.mCurrentAltitudeProfileBitmap = result;
					showDialog(DIALOG_SHOW_ALTITUDE_PROFILE);
				}
			}.execute(this.mRoute);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		menu.findItem(MENU_PRELOADTILES_ID).setEnabled(this.mRoute != null);

		menu.findItem(MENU_SHAREROUTE_ID).setVisible(this.mRoute != null && this.mRoute.hasRouteHandleID());

		final MenuItem refetchMenuItem = menu.findItem(MENU_REFETCH_ROUTE_ID);
		refetchMenuItem.setVisible(!this.mRealtimeNav || !this.mDoAutomaticRouteRefetch);

		if(!this.mDoAutomaticRouteRefetch) {
			refetchMenuItem.setTitle(R.string.maps_menu_auto_refetch);
		} else if(!this.mRealtimeNav) {
			refetchMenuItem.setTitle(R.string.maps_menu_refetch_route);
		}

		final MenuItem reverseRouteMenuItem = menu.findItem(MENU_REVERSEROUTE_ID);
		reverseRouteMenuItem.setEnabled(this.mGPStart != null && this.mGPDestination != null);

		return super.onPrepareOptionsMenu(menu);
	}

	private void startPreloadTilesDialog() {
		startPreloadTilesDialog(this.mOSMapView.getZoomLevel());
	}

	private void startPreloadTilesDialog(final int zoomLevel) {
		if(this.mRoute == null){
			Toast.makeText(this, R.string.toast_preloader_noroute, Toast.LENGTH_SHORT).show();
			return;
		}

		final MapTileProviderBase rendererInfo = this.mOSMapView.getTileProvider();

		final ArrayList<MapTile> tilesNeeded = OSMMapTilePreloader.getNeededMaptiles(this.mRoute, zoomLevel, rendererInfo, true);

        final int bytesEpectedNeeded = tilesNeeded.size() * rendererInfo.getTileSource().getTileSizePixels() * 71;
		final String formattedFileSize = FileSizeFormatter.formatFileSize(bytesEpectedNeeded);

		new AlertDialog.Builder(this)
		.setTitle(R.string.dlg_preloader_title)
        .setMessage(String.format(getString(R.string.dlg_preloader_message), tilesNeeded.size(), formattedFileSize))
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
			@Override
			public void onClick(final DialogInterface d, final int which) {
				d.dismiss();
				final String progressMessage = getString(R.string.pdg_preloader_message);
				final ProgressDialog pd = ProgressDialog.show(OpenStreetDDMap.this, getString(R.string.pdg_preloader_title), String.format(progressMessage,0,tilesNeeded.size()), true, true);
				final OSMMapTilePreloader preloader = new OSMMapTilePreloader(OpenStreetDDMap.this, rendererInfo.getTileSource(), tilesNeeded);
				preloader.setHandler(new Handler(){
					@Override
					public void handleMessage(Message msg) {
						int progress = preloader.getProgress();
						int total = preloader.getTotal();
						if(progress < total)
							pd.setMessage(String.format(progressMessage, progress, total));
						else
							pd.dismiss();
					}
				});
				new Thread(preloader).start();
				if(OpenStreetDDMap.this.mAutoZoomEnabled){
					OpenStreetDDMap.this.mAutoZoomEnabled = false;
					Preferences.saveAutoZoomEnabled(OpenStreetDDMap.this, false);
					Toast.makeText(OpenStreetDDMap.this, R.string.ddmap_info_preloader_autozoom_disabled, Toast.LENGTH_LONG).show();
				}
			}
		})
		.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){
			@Override
			public void onClick(final DialogInterface d, final int which) {
				d.dismiss();
			}
		}).show();
	}

	/**
	 * Starts the SDMainChoose-Activity for result with MODE_SD = MODE_SD_WAYPOINT and the requestcode REQUESTCODE_SD_WAYPOINT.
	 */
	private void startWaypointActivity() {
		final Intent sdIntent = new Intent(this, SDMainChoose.class);
		final Bundle b = new Bundle();
		b.putInt(MODE_SD, MODE_SD_WAYPOINT);
		sdIntent.putExtras(b);

		startActivityForResult(sdIntent, REQUESTCODE_SD_WAYPOINT);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem aItem) {
		/* Switch on the ID of the item, to get what the user selected. */
		switch (aItem.getItemId()) {
			case CONTEXTMENU_ADDASWAYPOINT:
				this.mWayPoints.add(this.mGPLastMapClick);
				// will cause a Route-Refetch
				this.kickOffRouteFetch();
				return true;
			case CONTEXTMENU_CLEARWAYPOINTS:
				this.mWayPoints.clear();
				// will cause a Route-Refetch
				this.kickOffRouteFetch();
				return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
			case REQUESTCODE_ROUTINGFLAGS:
				if (resultCode == SUBACTIVITY_RESULTCODE_CHAINCLOSE_SUCCESS || resultCode == SUBACTIVITY_RESULTCODE_SUCCESS) {
					initDrivingDirections(INITDRIVINGDIRECTIONS_NEWROUTE);
				}else if (resultCode == SUBACTIVITY_RESULTCODE_CHAINCLOSE_QUITTED) {
					this.setResult(SUBACTIVITY_RESULTCODE_UP_ONE_LEVEL);
					this.finish();
				}
				break;
			case REQUESTCODE_SETTINGS:
				final int displayQuality = Preferences.getDisplayQuality(this);
				final DirectionArrowDescriptor pDirectionArrowDescriptor = Preferences.getHUDImplVariationDirectionArrowDescriptor(this);

				/* When returning from the Settings-subActivity, always reset the MapDrivingDirectionsOverlay. */
                final OverlayManager overlaymanager = this.mOSMapView.getOverlayManager();
				this.mMyMapDrivingDirectionsOverlay.release();
				overlaymanager.remove(this.mMyMapDrivingDirectionsOverlay);
				this.mMyMapDrivingDirectionsOverlay = new MapDrivingDirectionsOverlay(this, displayQuality, this.mRealtimeNav, pDirectionArrowDescriptor);
				overlaymanager.add(this.mMyMapDrivingDirectionsOverlay);

				/* Refresh possibly changed UnitSystems. */
				this.mHUDImpl.getRemainingSummaryView().setDisplayQuality(displayQuality);
				this.mHUDImpl.getNextActionView().setDisplayQuality(displayQuality);
				this.mHUDImpl.getUpperNextActionView().setDisplayQuality(displayQuality);

				final UnitSystem unitSystem = Preferences.getUnitSystem(this);
				this.mHUDImpl.getNextActionView().setUnitSystem(unitSystem);
				this.mHUDImpl.getUpperNextActionView().setUnitSystem(unitSystem);
				this.mHUDImpl.getRemainingSummaryView().setUnitSystem(unitSystem);
                if (Preferences.getUnitSystem(this) == UnitSystem.IMPERIAL) {
                    this.mScaleIndicatorView.setImperial();
                } else {
                    this.mScaleIndicatorView.setMetric();
                }

				break;
			case REQUESTCODE_SD_WAYPOINT:
				if (resultCode == SUBACTIVITY_RESULTCODE_CHAINCLOSE_SUCCESS || resultCode == SUBACTIVITY_RESULTCODE_SUCCESS) {
					final Bundle extras = data.getExtras();
					GeoPoint via = null;

					/* Switch on the Mode. */
					final int searchMode = extras.getInt(EXTRAS_MODE);
					switch (searchMode) {
						case EXTRAS_MODE_DIRECT_LATLNG:
							final int latE6 = extras.getInt(EXTRAS_DESTINATION_LATITUDE_ID);
							final int lonE6 = extras.getInt(EXTRAS_DESTINATION_LONGITUDE_ID);
							via = new GeoPoint(latE6, lonE6);
							break;
						default:
							throw new IllegalArgumentException("Unawaited SEARCHMODE!");
					}
					if(via == null){
						// TODO Show error
					} else {
						this.mWayPoints.add(via);
						this.kickOffRouteFetch();
					}
				}
				break;
		}
	}

	@Override
	public void onRouteResumed() {
		if(!this.mRealtimeNav) {
			return;
		}


		Log.d(Constants.DEBUGTAG, "Route Resumed.");

		this.mOnRouteStatus = ON_ROUTE;

		/* Remove any upcoming route-refetches from the Handler. */
		this.mRefetchTriggerHandler.removeCallbacks(this.mRefetchRunner);

		this.mRouteRefetchRunning = false;

		runOnUiThread(new Runnable(){
			@Override
			public void run() {
				if (OpenStreetDDMap.this.mDirectionVoiceEnabled) {
					OpenStreetDDMap.this.mSoundManager.playSound(R.raw.route_resumed);
				}

				OpenStreetDDMap.this.mIvRouteStatus.setVisibility(View.GONE);
			}
		});

	}

	@Override
	public void onRouteMissed() {
		if(!this.mRealtimeNav) {
			return;
		}

		this.mOnRouteStatus = OFF_ROUTE;

		runOnUiThread(new Runnable(){
			@Override
			public void run() {
				if (OpenStreetDDMap.this.mDirectionVoiceEnabled) {
					OpenStreetDDMap.this.mSoundManager.playSound(R.raw.route_missed);
				}

				OpenStreetDDMap.this.mIvRouteStatus.setImageDrawable(OpenStreetDDMap.this.mOffRouteDrawable);
				OpenStreetDDMap.this.mIvRouteStatus.setVisibility(View.VISIBLE);
			}
		});

		if (!this.mRouteRefetchRunning) {
			this.mRouteRefetchRunning = true;
			Log.d(Constants.DEBUGTAG, "Refetching Route in 8 seconds...");
			this.mRefetchTriggerHandler.postDelayed(this.mRefetchRunner, this.TIME_BETWEEN_MISS_REFETCH);
		} else {
			Log.d(Constants.DEBUGTAG, "No refetch, because: this.mRouteRefetchRunning == true");
		}
	}

	@Override
	public void onTargetReached() {
		if(!this.mRealtimeNav) {
			return;
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(OpenStreetDDMap.this.mDirectionVoiceEnabled) {
					OpenStreetDDMap.this.mSoundManager.playSound(R.raw.target_reached);
				}

				Toast.makeText(OpenStreetDDMap.this, R.string.ddmap_info_target_reached, Toast.LENGTH_LONG).show();
			}
		});
	}

	@Override
	public void onWaypointPassed(final List<GeoPoint> waypointsLeft) {
		if(!this.mRealtimeNav) {
			return;
		}

		/** Clear old waypoints and add the new ones passed by the Navigator. */
		this.mWayPoints.clear();
		for (final GeoPoint gp : waypointsLeft) {
			this.mWayPoints.add(gp);
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(OpenStreetDDMap.this.mDirectionVoiceEnabled) {
					OpenStreetDDMap.this.mSoundManager.playSound(R.raw.waypoint_passed);
				}

				Toast.makeText(OpenStreetDDMap.this, R.string.ddmap_info_waypoint_passed, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void onReceiveAudibleTurnCommand(final AudibleTurnCommand pATC) {
		if(!this.mRealtimeNav) {
			return;
		}

		/*
		 * No need to check: if(this.mDirectionVoiceEnabled) as in that case we
		 * are not registering ourself as a listener.
		 */
		if (this.mOnRouteStatus != ON_ROUTE) {
			return;
		}

		final UnitSystem us = Preferences.getUnitSystem(this);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final DistanceVoiceElement dve = pATC.getDistanceVoiceElement();
				final TurnVoiceElement tve = pATC.getTurnVoiceElement();
				final Integer voiceType = OpenStreetDDMap.this.mTurnVoiceSayList.get(dve.LENGTH_METERS);
				if(voiceType != null){
					int voiceTypeIntValue = voiceType.intValue();

					/* If TTS is not (yet) available, fallback to non-tts speech. */
					if(OpenStreetDDMap.this.mTTSAvailable == false){
						switch(voiceTypeIntValue){
							case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_DISTANCE:
								voiceTypeIntValue = PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_DISTANCE;
								break;
							case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_TURN:
								voiceTypeIntValue = PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_TURN;
								break;
							case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_DISTANCE_AND_TURN:
								voiceTypeIntValue = PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_DISTANCE_AND_TURN;
								break;
						}
					}
					/* Gets set on demand. */
					final String fullTurnTextImproved;
					final String distanceString = "In " + dve.LENGTH_UNITWISE + " " + dve.getUnitTextual(OpenStreetDDMap.this, OpenStreetDDMap.this.mDrivingDirectionsLanguage);

					final String thenString;
					if(pATC.hasThenCommand()){
						final SimpleAudibleTurnCommand satc = pATC.getThenCommand();
						final DistanceVoiceElement thenDVE = satc.getDistanceVoiceElement();
						final TurnVoiceElement thenTVE = satc.getTurnVoiceElement();
						final String tveText = thenTVE.getText(OpenStreetDDMap.this, OpenStreetDDMap.this.mDrivingDirectionsLanguage);
						if(thenDVE == null){
							final String thenStringRaw = OpenStreetDDMap.this.mDrivingDirectionsLanguage.getThenCommandWithoutDistance(OpenStreetDDMap.this, RoutePreferenceType.FASTEST); // TODO get RoutePreferenceType from bundle
							thenString = ". " + thenStringRaw.replace("%s", tveText);
						}else{
							final UnitSystem us = Preferences.getUnitSystem(OpenStreetDDMap.this);
							final String thenStringRaw = OpenStreetDDMap.this.mDrivingDirectionsLanguage.getThenCommandWithDistance(OpenStreetDDMap.this, RoutePreferenceType.FASTEST); // TODO get RoutePreferenceType from bundle
							final String[] distanceStringFull = us.getDistanceStringFull(OpenStreetDDMap.this, OpenStreetDDMap.this.mDrivingDirectionsLanguage, null, thenDVE.LENGTH_UNITWISE);
							thenString = ". " + thenStringRaw.replace("%d", "" + thenDVE.LENGTH_UNITWISE + " " + distanceStringFull[UnitSystem.DISTSTRINGS_UNIT_ID]).replace("%s", tveText);
						}
					}else{
						thenString = "";
					}

					switch(voiceTypeIntValue){
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_DISTANCE:
							OpenStreetDDMap.this.mSoundManager.playSound(us.convertFromMetricDistanceVoice(dve).RESID);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_TURN:
							OpenStreetDDMap.this.mSoundManager.playSound(tve.VOICERESID);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_DISTANCE_AND_TURN:
							OpenStreetDDMap.this.mSoundManager.playFollowUpSounds(us.convertFromMetricDistanceVoice(dve).RESID, tve.VOICERESID);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_DISTANCE:
							OpenStreetDDMap.this.mTTS.speak(distanceString + thenString, 0, null);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_TURN:
							fullTurnTextImproved = SpeechImprover.improve(pATC.getFullTurnText(), OpenStreetDDMap.this.mRouteCountry);
							OpenStreetDDMap.this.mTTS.speak(fullTurnTextImproved + thenString, 0, null);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SPEECH_DISTANCE_AND_TURN:
							fullTurnTextImproved = SpeechImprover.improve(pATC.getFullTurnText(), OpenStreetDDMap.this.mRouteCountry);
							OpenStreetDDMap.this.mTTS.speak(distanceString + ". " + fullTurnTextImproved + thenString, 0, null);
							break;
						case PreferenceConstants.PREF_TURNVOICESAYLIST_SAY_NOTHING:
							return;
						default:
							throw new IllegalArgumentException("Default branch in onReceiveAudibleTurnCommand()");
					}
				}
			}
		});
	}

	@Override
	protected void onPause() {
		Log.d(Constants.DEBUGTAG, "OnPAUSE");
		super.onPause();
	}

	/**
	 * Load all the settings, that could have changed.
	 */
	@Override
	protected void onResume() {
		super.onResume();

		final boolean realtimeNavBeforeResume = this.mRealtimeNav;
		this.mRealtimeNav = Preferences.getRealTimeNav(this);
		if(realtimeNavBeforeResume != this.mRealtimeNav) {
			initDrivingDirections(INITDRIVINGDIRECTIONS_NEWROUTE);
		}

		Log.d(Constants.DEBUGTAG, "OnRESUME");
		this.mRouteRefetchRunning = false;
		this.mNavigator.forceOffRouteListenerUpdateInNextTick();

		this.mCenterMode = Preferences.getCenterMode(this);
		this.mRotateMode = Preferences.getRotateMode(this);
		this.mSnapToRouteEnabled = Preferences.getSnapToRoute(this);

		this.mSnapToRouteRadius = Preferences.getSnapToRouteRadius(this);
		NavAlgorithm.setDISTANCE_TO_TOGGLE_OFF_ROUTE(this.mSnapToRouteRadius);

		this.mAutoZoomEnabled = Preferences.getAutoZoomEnabled(this);
		this.mDirectionVoiceEnabled = Preferences.getDirectionVoiceEnabled(this);
		this.mStatisticsEnabled = Preferences.getStatisticsEnabled(this);
		this.mDrivingDirectionsLanguage = Preferences.getDrivingDirectionsLanguage(this);
		if(this.mRouteCountry == null) {
			this.mRouteCountry = this.mDrivingDirectionsLanguage.getMotherCountry();
		}

		final UnitSystem unitSystem = Preferences.getUnitSystem(this);

		if(this.mStatisticsEnabled) {
			this.mStatisticsManager = new StatisticsManager(this, unitSystem, Preferences.getStatisticsSessionStart(this));
		} else{
			this.mStatisticsManager = null;
		}

		if (this.mRealtimeNav && this.mDirectionVoiceEnabled){
			this.mTurnVoiceSayList = Preferences.getTurnVoiceSayList(this);
			this.mNavigator.setUnitSystem(unitSystem);
			this.mNavigator.setDistanceVoiceCommandListener(this);
		}


		this.mMyMapDrivingDirectionsOverlay.setRealtimeNav(this.mRealtimeNav);
		this.mMyMapDrivingDirectionsOverlay.setMapRotationDegree(Constants.NOT_SET);
		((this.mMapRotateView)).setRotationDegree(Constants.NOT_SET);
	}

	@Override
	protected void onRestoreInstanceState(final Bundle inState) {
		super.onRestoreInstanceState(inState);

		this.mWayPoints = inState.getParcelableArrayList(STATE_WAYPOINTS_ID);
		this.mStaticNavCurrentTurnIndex = inState.getInt(STATE_STATICNAVCURRENT_ID);
		this.mStaticNavNextTurnIndex = inState.getInt(STATE_STATICNAVNEXT_ID);
		this.mDoAutomaticRouteRefetch = inState.getBoolean(STATE_DOAUTOMATICROUTEREFETCH_ID);
		this.mInitialRouteFetch = inState.getBoolean(STATE_INITIALROUTEFETCH_ID);
		this.mRoute = (Route)inState.getParcelable(STATE_ROUTE_ID);
		this.mLastWorkingRoute = (Route)inState.getParcelable(STATE_LASTWORKINGROUTE_ID);
		initStaticNavControlsIfNeccessary();
		initDrivingDirections(INITDRIVINGDIRECTIONS_RESTORE);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		Log.d(Constants.DEBUGTAG, "OnFREEZE");

		outState.putParcelableArrayList(STATE_WAYPOINTS_ID, this.mWayPoints);
		outState.putInt(STATE_STATICNAVCURRENT_ID, this.mStaticNavCurrentTurnIndex);
		outState.putInt(STATE_STATICNAVNEXT_ID, this.mStaticNavNextTurnIndex);
		outState.putBoolean(STATE_DOAUTOMATICROUTEREFETCH_ID, this.mDoAutomaticRouteRefetch);
		outState.putBoolean(STATE_INITIALROUTEFETCH_ID, this.mInitialRouteFetch);
		outState.putParcelable(STATE_ROUTE_ID, this.mRoute);
		outState.putParcelable(STATE_LASTWORKINGROUTE_ID, this.mLastWorkingRoute);

		this.mRefetchTriggerHandler.removeCallbacks(this.mRefetchRunner);

		if(this.mStatisticsEnabled && this.mStatisticsManager != null) {
			this.mStatisticsManager.writeThrough();
		}

		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		Log.d(Constants.DEBUGTAG, "OnDESTROY");
		this.mTTS.shutdown();

		this.mRefetchTriggerHandler.removeCallbacks(this.mRefetchRunner);

		this.mWakeLock.release();
		this.mSoundManager.releaseAll();

		if(this.mStatisticsManager != null) {
			this.mStatisticsManager.finish();
		}

		super.onDestroy();
	}

	@Override
	public void onLocationLost(final AndNavLocation pLocation) {
		// TODO anzeigen...
	}

	@Override
	public void onLocationChanged(final AndNavLocation pLocation) {
		if(super.mOSMapView == null) {
			return;
		}

		try{
			if(this.mStatisticsEnabled && this.mStatisticsManager != null) {
				this.mStatisticsManager.tick(pLocation);
			}

			/* Advanced Logic to determine, where we are on our route. */
			if(this.mNavigator != null) {
				this.mNavigator.tick(pLocation);
			}

			if(!this.mRealtimeNav){
				super.mOSMapView.postInvalidate();
				return;
			}

			/* Redraw the whole mapView, which also makes
			 * our Overlay(s) being redrawn. */

			sendRotationToChildren();
			refreshTurnText();
			refreshHUD();

			final long now = System.currentTimeMillis();
			if(now > this.mAutoCenterBlockedUntil){
				switch(this.mCenterMode) {
					case PREF_CENTERMODE_CENTERUSER:
						if (this.mNavigator.isReady()) {
							zoomAndCenterOnLocation(true);
							break;
						}
						break;
					case PREF_CENTERMODE_UPTO_NEXTTURN:
						if (this.mNavigator.isReady()) {
							zoomAndCenterBetweenLocationAndNextTurn();
							break;
						}
				}
			}
			super.mOSMapView.postInvalidate();
		}catch(final Exception e){
			Exceptor.e("Error in onLocationChanged()", e, this);
		}
	}

	private void refreshHUD() {
		try{
			if(!this.mRealtimeNav){
				if(this.mRoute == null || this.mStaticNavCurrentTurnIndex == Constants.NOT_SET){
					this.mMapItemControlView.setPreviousEnabled(false);
					this.mMapItemControlView.setNextEnabled(false);

					this.mMyMapDrivingDirectionsOverlay.setStaticNavCurrentTurnPointIndex(Constants.NOT_SET);

					this.mHUDImpl.getNextActionView().reset();
					this.mHUDImpl.getRemainingSummaryView().reset();
					if(this.mStatisticsManager != null){
						this.mHUDImpl.getNextActionView().setCurrentMeterSpeed((int)this.mStatisticsManager.getCurrentSpeed());
						this.mHUDImpl.getRemainingSummaryView().setMetersDrivenSession((int)this.mStatisticsManager.getMetersScaleDrivenSession());
					}
				}else{
					final List<RouteInstruction> tps = this.mRoute.getRouteInstructions();
					final int tpsSizeLessOne = tps.size() - 1;

					final int staticNavCurrentTurnIndex = Math.min(this.mStaticNavCurrentTurnIndex, tpsSizeLessOne);
					final int staticNavNextTurnIndex = Math.min(this.mStaticNavNextTurnIndex, tpsSizeLessOne);

					this.mMapItemControlView.setPreviousEnabled(staticNavCurrentTurnIndex > 0);

					this.mMapItemControlView.setNextEnabled(staticNavNextTurnIndex < tpsSizeLessOne);

					this.mMyMapDrivingDirectionsOverlay.setStaticNavCurrentTurnPointIndex(staticNavCurrentTurnIndex);

					final RouteInstruction currentTurnPoint = tps.get(staticNavCurrentTurnIndex);

					final RouteInstruction nextTurnPoint = tps.get(staticNavNextTurnIndex);

					this.mHUDImpl.getNextActionView().setDistance(currentTurnPoint.getLengthMeters());

					final int staticNavCurrentTurnIndexInRoute = currentTurnPoint.getFirstMotherPolylineIndex();
					this.mHUDImpl.getRemainingSummaryView().setDistance(this.mRoute.getRouteSegmentLengthsUpToDestination()[staticNavCurrentTurnIndexInRoute]);

					int dur = 0;
					for(int i = tpsSizeLessOne; i >= this.mStaticNavCurrentTurnIndex; i--) {
						dur += tps.get(i).getDurationSeconds();
					}

					this.mHUDImpl.getRemainingSummaryView().setEstimatedRestSeconds(dur);

					this.mHUDImpl.getTurnTurnDescriptionView().setTurnDescription(nextTurnPoint.getDescription());

					/* ALways show the angle, except on the very last one, show the Flag. */
					if(staticNavNextTurnIndex == tpsSizeLessOne){
						this.mHUDImpl.getNextActionView().showTargetReached();
						this.mHUDImpl.setUpperNextActionViewNecessary(false);
					} else if(staticNavNextTurnIndex + 1 == tpsSizeLessOne){
						/* The uppernext one is the target. */
						this.mHUDImpl.getNextActionView().setTurnAngle(nextTurnPoint.getAngle());

						this.mHUDImpl.setUpperNextActionViewNecessary(true);
						this.mHUDImpl.getUpperNextActionView().showTargetReached();
					}else{
						/* Both are visible. */
						this.mHUDImpl.getNextActionView().setTurnAngle(nextTurnPoint.getAngle());

						final RouteInstruction upperNextTurnPoint = tps.get(staticNavNextTurnIndex + 1);
						this.mHUDImpl.setUpperNextActionViewNecessary(true);
						this.mHUDImpl.getUpperNextActionView().setTurnAngle(upperNextTurnPoint.getAngle());
						this.mHUDImpl.getUpperNextActionView().setDistance(nextTurnPoint.getLengthMeters());
					}
				}
			}else{
				final Navigator nav = this.mNavigator; // Drag to local field
				if(this.mRoute == null || !nav.isReady()){
					this.mHUDImpl.getNextActionView().reset();
					this.mHUDImpl.getRemainingSummaryView().reset();
					if(this.mStatisticsManager != null){
						this.mHUDImpl.getNextActionView().setCurrentMeterSpeed((int)this.mStatisticsManager.getCurrentSpeed());
						this.mHUDImpl.getRemainingSummaryView().setMetersDrivenSession((int)this.mStatisticsManager.getMetersScaleDrivenSession());
					}
				}else{
					final List<RouteInstruction> tps = this.mRoute.getRouteInstructions();
					final int tpsSizeLessOne = tps.size() - 1;

					this.mHUDImpl.getNextActionView().setDistance(nav.getDistanceToNextTurnPoint());

					this.mHUDImpl.getRemainingSummaryView().setDistance(nav.getTotalRestDistance());
					this.mHUDImpl.getRemainingSummaryView().setEstimatedRestSeconds(nav.getEstimatedRestSeconds());


					/* ALways show the angle, except on the very last one, show the Flag. */
					final int nextTurnPointIndex = nav.getNextTurnPointIndex();
					if(nextTurnPointIndex == tpsSizeLessOne || nextTurnPointIndex == Constants.NOT_SET){
						this.mHUDImpl.getNextActionView().showTargetReached();
						this.mHUDImpl.setUpperNextActionViewNecessary(false);
					} else if(nextTurnPointIndex + 1 == tpsSizeLessOne){
						/* The uppernext one is the target. */
						this.mHUDImpl.getNextActionView().setTurnAngle(nav.getTurnAngle());

						this.mHUDImpl.setUpperNextActionViewNecessary(true);
						this.mHUDImpl.getUpperNextActionView().showTargetReached();
					}else{
						/* Both are visible. */
						this.mHUDImpl.getNextActionView().setTurnAngle(nav.getTurnAngle());

						this.mHUDImpl.setUpperNextActionViewNecessary(true);
						final RouteInstruction nextTurnPoint = tps.get(nextTurnPointIndex);
						final RouteInstruction upperNextTurnPoint = tps.get(nextTurnPointIndex + 1);
						this.mHUDImpl.getUpperNextActionView().setTurnAngle(upperNextTurnPoint.getAngle());
						this.mHUDImpl.getUpperNextActionView().setDistance(nextTurnPoint.getLengthMeters());
					}
				}
			}

			if(super.mLocationProvider.hasNumberOfLandmarks()){
				this.mHUDImpl.getRemainingSummaryView().setGPSConnectionStrength(super.mLocationProvider.getNumberOfLandmarks());
			}else{
				this.mHUDImpl.getRemainingSummaryView().setGPSConnectionStrength(0);
			}

			this.mHUDImpl.invalidateViews();
		}catch(final Exception e){
			Exceptor.e("HudRefresh-Error", e, this);
		}
	}

	private void refreshTurnText() {
		/* Update the textView with the turn-description. */
		if(this.mRoute == null || !this.mNavigator.isReady()){
			this.mHUDImpl.getTurnTurnDescriptionView().reset();
		}else{
			final int nextTurnPointIndex = this.mNavigator.getNextTurnPointIndex();
			if(nextTurnPointIndex != Constants.NOT_SET && nextTurnPointIndex < this.mRoute.getRouteInstructions().size()) {
				this.mHUDImpl.getTurnTurnDescriptionView().setTurnDescription(this.mRoute.getRouteInstructions().get(nextTurnPointIndex).getDescription());
			}
		}
	}

	private void zoomAndCenterOnLocation(final boolean animateNotJustCenter) {
		/* Drag to local field. */
		if(!super.mLocationProvider.hasLastKnownLocation()) {
			return;
		}

		final AndNavLocation lastKnownExcatLocation = super.mLocationProvider.getLastKnownLocation();

		/* Just center to the current location. */
		if(animateNotJustCenter) {
			this.mOSMapView.getController().animateTo(lastKnownExcatLocation, AnimationType.MIDDLEPEAKSPEED);
		} else {
			super.mOSMapView.getController().setCenter(lastKnownExcatLocation);
		}

		if(this.mAutoZoomEnabled && this.mNavigator.isReady() && this.mRoute != null && System.currentTimeMillis() > this.mAutoZoomBlockedUntil){
			final int nextRoutePoint = this.mNavigator.getNextRoutePointIndex();

			if(nextRoutePoint != Constants.NOT_SET){
				final int myLatE6 = lastKnownExcatLocation.getLatitudeE6();
				final int myLonE6 = lastKnownExcatLocation.getLongitudeE6();

				final int newMaxLat = Math.max(myLatE6, this.mRoute.getLatitudeMaxSpan(nextRoutePoint));
				final int newMinLat = Math.min(myLatE6, this.mRoute.getLatitudeMinSpan(nextRoutePoint));
				final int newMaxLon = Math.max(myLonE6, this.mRoute.getLongitudeMaxSpan(nextRoutePoint));
				final int newMinLon = Math.min(myLonE6, this.mRoute.getLongitudeMinSpan(nextRoutePoint));

				final int reqLatSpan = (int)(1.2f * (newMaxLat - newMinLat)); // 20% Margin
				final int reqLonSpan = (int)(1.2f * (newMaxLon - newMinLon)); // 20% Margin

				this.mOSMapView.getController().zoomToSpan(reqLatSpan, reqLonSpan);
			}
		}
	}

	private void zoomAndCenterBetweenLocationAndNextTurn() {
		if(!this.mNavigator.isReady() || this.mNavigator.getNextTurnPointIndex() == Constants.NOT_SET){
			zoomAndCenterOnLocation(true);
		}else{

			if(super.mLocationProvider.hasLastKnownLocation()){
				final GeoPoint nextTurnPoint = this.mNavigator.getNextTurnPointIndexAsGeoPoint();

				final int nextRoutePointIndex = this.mNavigator.getNextRoutePointIndex();
				if(nextRoutePointIndex != Constants.NOT_SET) {
					centerAndZoomBetween(nextRoutePointIndex, super.mLocationProvider.getLastKnownLocation(), nextTurnPoint, true);
				}
			}
		}
	}

	private void centerAndZoomBetween(final int aRoutePointIndex, final GeoPoint aFirstGP, final GeoPoint pGP, final boolean animateNotJustCenter){
		if(aRoutePointIndex == Constants.NOT_SET || pGP == null) {
			return;
		}

		final int myLatE6 = aFirstGP.getLatitudeE6();
		final int myLonE6 = aFirstGP.getLongitudeE6();

		final int centerLat = (pGP.getLatitudeE6() + myLatE6) >> 1; // ">> 1" == "/ 2"
				final int centerLon = (pGP.getLongitudeE6() + myLonE6) >> 1; // ">> 1" == "/ 2"


				final GeoPoint newCenter = new GeoPoint(centerLat, centerLon);

				if(animateNotJustCenter) {
					this.mOSMapView.getController().animateTo(newCenter, AnimationType.MIDDLEPEAKSPEED);
				} else {
					this.mOSMapView.getController().setCenter(newCenter);
				}


				final Route route = this.mRoute;
				if(this.mAutoZoomEnabled && this.mNavigator.isReady() && route != null && System.currentTimeMillis() > this.mAutoZoomBlockedUntil){
					/* Zoom to the middle of the current location and the next turnpoint. */

					final int newMaxLat = Math.max(myLatE6, route.getLatitudeMaxSpan(aRoutePointIndex));
					final int newMinLat = Math.min(myLatE6, route.getLatitudeMinSpan(aRoutePointIndex));
					final int newMaxLon = Math.max(myLonE6, route.getLongitudeMaxSpan(aRoutePointIndex));
					final int newMinLon = Math.min(myLonE6, route.getLongitudeMinSpan(aRoutePointIndex));

					final int reqLatSpan = (int)(1.2f * (newMaxLat - newMinLat)); // 20% Margin
					final int reqLonSpan = (int)(1.2f * (newMaxLon - newMinLon)); // 20% Margin

                    this.mOSMapView.getController().zoomToSpan(reqLatSpan, reqLonSpan);
				}
	}

	private void sendRotationToChildren() {
		if (this.mRotateMode == PREF_ROTATEMODE_DRIVINGDIRECTION_UP && this.mLocationProvider.hasBearing()) {
			final float bearing = this.mLocationProvider.getBearing();
			this.mMapRotateView.setRotationDegree(bearing);
			this.mMyMapDrivingDirectionsOverlay.setMapRotationDegree(bearing); // TODO <-- Neccessary ???
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void applyViewListeners() {
		this.findViewById(R.id.iv_ddmap_zoomin).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				OpenStreetDDMap.super.mOSMapView.getController().zoomIn();
				OpenStreetDDMap.super.mOSMapView.invalidate();
				OpenStreetDDMap.this.mAutoZoomBlockedUntil = System.currentTimeMillis() + AUTOZOOM_BLOCKTIME;
			}
		});
		this.findViewById(R.id.iv_ddmap_zoomout).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				OpenStreetDDMap.super.mOSMapView.getController().zoomOut();
				OpenStreetDDMap.super.mOSMapView.invalidate();
				OpenStreetDDMap.this.mAutoZoomBlockedUntil = System.currentTimeMillis() + AUTOZOOM_BLOCKTIME;
			}
		});

		this.mHUDImpl.getRemainingSummaryView().setRemainingSummaryOnClickListener(new OnClickListener(){
			@Override
			public void onClick(final View v) {
				OpenStreetDDMap.this.mHUDImpl.getRemainingSummaryView().onClick();
			}
		});

		final OnClickListener speakTurnOnClickListener = new OnClickListener(){
			@Override
			public void onClick(final View v) {
				speakTurn();
			}
		};
		this.mHUDImpl.getNextActionView().setNextActionOnClickListener(speakTurnOnClickListener);
		this.mHUDImpl.getUpperNextActionView().setNextActionOnClickListener(speakTurnOnClickListener);
		this.mHUDImpl.getTurnTurnDescriptionView().setTurnDescriptionOnClickListener(speakTurnOnClickListener);
	}

	/** Speaks out the next turn via TTS (If Navigator is ready). */
	private void speakTurn() {
		final String turnDescription = this.mHUDImpl.getTurnTurnDescriptionView().getTurnDescription().toString();
		if(OpenStreetDDMap.this.mNavigator.isReady() && turnDescription != null && turnDescription.length() > 0){
			final UnitSystem us = Preferences.getUnitSystem(OpenStreetDDMap.this);
			final String[] lengthAndUnit;
			if(OpenStreetDDMap.this.mRealtimeNav){
				lengthAndUnit = us.getDistanceStringFull(OpenStreetDDMap.this, this.mDrivingDirectionsLanguage, null, OpenStreetDDMap.this.mNavigator.getDistanceToNextTurnPoint());
			}else{ /* Static Navigation. */
				final RouteInstruction nextInstruction = OpenStreetDDMap.this.mRoute.getRouteInstructions().get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex);
				lengthAndUnit = us.getDistanceStringFull(OpenStreetDDMap.this, this.mDrivingDirectionsLanguage, null, nextInstruction.getLengthMeters());
			}
			final String textToSay = "In " + lengthAndUnit[UnitSystem.DISTSTRINGS_DIST_ID] + " " + lengthAndUnit[UnitSystem.DISTSTRINGS_UNIT_ID] + ", " + turnDescription;

			OpenStreetDDMap.this.mTTS.speak(SpeechImprover.improve(textToSay, OpenStreetDDMap.this.mRouteCountry), 0, null);
		}
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
		menu.setHeaderTitle("ContextMenu");
		menu.add(0, CONTEXTMENU_ADDASWAYPOINT, Menu.NONE, R.string.maps_contextmenu_add_as_waypoint).setIcon(R.drawable.wipe);
		menu.add(1, CONTEXTMENU_CLEARWAYPOINTS, Menu.NONE, R.string.maps_contextmenu_clear_waypoints).setIcon(R.drawable.optimize);
		menu.add(2, CONTEXTMENU_CLOSE, Menu.NONE, R.string.maps_contextmenu_add_as_waypoint).setIcon(R.drawable.close);
		/* Add as many context-menu-options as you want to. */
	}

	private void applyMapViewLongPressListener() {
		final GestureDetector gd = new GestureDetector(new GestureDetector.SimpleOnGestureListener(){
			@Override
			public void onLongPress(final MotionEvent e) {
				final Projection pj = OpenStreetDDMap.super.mOSMapView.getProjection();
				OpenStreetDDMap.this.mGPLastMapClick = pj.fromPixels((int)e.getX(), (int)e.getY());

				final String[] items = new String[]{getString(R.string.ddmap_contextmenu_add_as_waypoint),
						getString(R.string.ddmap_contextmenu_add_as_avoidarea),
						getString(R.string.ddmap_contextmenu_clear_waypoints),
						getString(R.string.ddmap_contextmenu_clear_avoidareas),
						getString(R.string.ddmap_contextmenu_close),
						getString(R.string.ddmap_contextmenu_exit)};
				new AlertDialog.Builder(OpenStreetDDMap.this)
				.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener(){

					@Override
					public void onClick(final DialogInterface d, final int which) {
						d.dismiss();
						switch(which){
							case 0:
								OpenStreetDDMap.this.mWayPoints.add(OpenStreetDDMap.this.mGPLastMapClick);
								/* Cause a Route-Refetch. */
								OpenStreetDDMap.this.kickOffRouteFetch();
								break;
							case 1:
								final UnitSystem us = Preferences.getUnitSystem(OpenStreetDDMap.this);

								final int[] valDist;
								switch(us){
									case IMPERIAL:
										valDist = getResources().getIntArray(R.array.poi_avoidarea_radius_ors_imperial);
										break;
									case METRIC:
									default:
										valDist = getResources().getIntArray(R.array.poi_avoidarea_radius_ors_metric);
								}

								final String[] valStr = new String[valDist.length];

								for (int i = 0; i < valDist.length; i++){
									final int cur = valDist[i];
									if(cur == SDPOISearchList.POISEARCH_RADIUS_GLOBAL){
										valStr[i] = getString(R.string.poi_search_radius_global);
									}else{
										final String[] distStringParts = us.getDistanceString(cur, null);
										valStr[i] = distStringParts[UnitSystem.DISTSTRINGS_DIST_ID] + distStringParts[UnitSystem.DISTSTRINGS_UNIT_ID];
									}
								}

								new AlertDialog.Builder(OpenStreetDDMap.this)
								.setTitle(R.string.ddmap_contextmenu_clear_avoidareas_radius_title)
								.setSingleChoiceItems(valStr, 0, new DialogInterface.OnClickListener(){
									@Override
									public void onClick(final DialogInterface d, final int which) {
										OpenStreetDDMap.this.mAvoidAreas.add(new CircleByCenterPoint(OpenStreetDDMap.this.mGPLastMapClick, valDist[which]));
										/* Cause a Route-Refetch. */
										OpenStreetDDMap.this.kickOffRouteFetch();
										d.dismiss();
									}
								})
								.create().show();
								break;
							case 2:
								OpenStreetDDMap.this.mWayPoints.clear();
								/* Cause a Route-Refetch. */
								OpenStreetDDMap.this.kickOffRouteFetch();
								break;
							case 3:
								OpenStreetDDMap.this.mAvoidAreas.clear();
								/* Cause a Route-Refetch. */
								OpenStreetDDMap.this.kickOffRouteFetch();
								break;
							case 4:
								break;
							case 5:
								OpenStreetDDMap.this.finish();
								break;
						}
					}
				})
				.create().show();
			}
		});
		this.mOSMapView.setOnTouchListener(new OnTouchListener(){
			@Override
			public boolean onTouch(final View v, final MotionEvent ev) {
				OpenStreetDDMap.this.mAutoCenterBlockedUntil = System.currentTimeMillis() + AUTOCENTER_BLOCKTIME;
				return gd.onTouchEvent(ev);
			}
		});
	}

	/**
	 * Calls <code>kickOffRouteFetch(null, INITDRIVINGDIRECTIONS_NEWROUTE);</code>
	 */
	private void kickOffRouteFetch() {
		kickOffRouteFetch(null, false);
	}

	/**
	 * Calls <code>kickOffRouteFetch(null, pRestore);</code>
	 */
	private void kickOffRouteFetch(final boolean pRestore) {
		kickOffRouteFetch(null, pRestore);
	}

	/**
	 * Calls <code>kickOffRouteFetch(pGPUseAsStart, INITDRIVINGDIRECTIONS_NEWROUTE);</code>
	 */
	private void kickOffRouteFetch(final GeoPoint pGPUseAsStart) {
		kickOffRouteFetch(pGPUseAsStart, INITDRIVINGDIRECTIONS_NEWROUTE);
	}

	/**
	 * 
	 * @param pGPUseAsStart
	 * @param pRestore passing <code>true</code> will cause the
	 */
	private void kickOffRouteFetch(final GeoPoint pGPUseAsStart, final boolean pRestore) {
		try {
			final String dialogMessage;
			if (this.mInitialRouteFetch) {
				// Display an indeterminate Progress-Dialog
				dialogMessage = getString(R.string.pdg_fetchroute_messagestart) + '\n' + getDialogMessage();
			} else {
				dialogMessage = getString(R.string.please_wait_a_moment);
			}
			// Display an indeterminate Progress-Dialog
			this.mRouteFetchProgressDialog = ProgressDialog.show(OpenStreetDDMap.this,
					getString(R.string.pdg_refetchroute_title),
					dialogMessage,
					true,
					true,
					new OnCancelListener(){
				@Override
				public void onCancel(final DialogInterface dialog) {
					OpenStreetDDMap.this.finish();
				}
			});

			this.mStaticNavCurrentTurnIndex = Constants.NOT_SET;
			this.mStaticNavNextTurnIndex = Constants.NOT_SET;
			this.mNavigator.setReady(false);

			/* Next Nav-Tick will trigger it back to ON or OFF Route. */
			OpenStreetDDMap.this.mOnRouteStatus = REFETCHING_ROUTE;

			new Thread(new Runnable() {
				@Override
				public void run() {
					boolean doRetry = true;
					int count = 0;

					if(OpenStreetDDMap.this.mRoute != null) {
						OpenStreetDDMap.this.mLastWorkingRoute = OpenStreetDDMap.this.mRoute;
					}

					do {
						if(pGPUseAsStart != null){
							OpenStreetDDMap.this.mGPStart = pGPUseAsStart;
						}else{
							OpenStreetDDMap.this.setProgressDialogTitle(R.string.pdg_refetchroute_title);

							ensureOwnGPSPosition();

							if(OpenStreetDDMap.this.mLocationProvider.hasLastKnownLocation()){
								OpenStreetDDMap.this.mGPStart = OpenStreetDDMap.this.mLocationProvider.getLastKnownLocation();
							}else{
								askRetryForReason(R.string.ddmap_info_gps_fetch_tries_exceeded_title, R.string.ddmap_info_gps_fetch_tries_exceeded_message);
								return;
							}
						}

						if(pRestore){
							OpenStreetDDMap.this.setProgressDialogTitle(R.string.pdg_restoreroute_title);
						}else{
							if(OpenStreetDDMap.this.mInitialRouteFetch) {
								OpenStreetDDMap.this.setProgressDialogTitle(R.string.pdg_fetchroute_title);
							} else {
								OpenStreetDDMap.this.setProgressDialogTitle(R.string.pdg_refetchroute_title);
							}
						}

						//						if (OpenStreetDDMap.this.mDataConnectionStrength == 0) { // TODO Muss wohl raus wegen WiFi!
						//							askRetryForReason(R.string.ddmap_info_route_fetch_no_dataconnection_title, R.string.ddmap_info_route_fetch_no_dataconnection_message);
						//							return;
						//						}

						/* Use our own GPS-Position */

						Route aRoute = null;

						try {
							if(pRestore){
								aRoute = OpenStreetDDMap.this.mRoute;
							}else{
								Log.d(Constants.DEBUGTAG, "BEFORE Creating ROute");
								aRoute = createRouteFromBundleCreatedWith();
								Log.d(Constants.DEBUGTAG, "AFTER Creating ROute");
							}

							/* No Exception, no need to retry. */
							doRetry = false;
						} catch (final ORSException nrfe) {
							Log.d(Constants.DEBUGTAG, "ERROR Creating ROute");
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(OpenStreetDDMap.this, nrfe.getErrors().get(0).toString(), Toast.LENGTH_SHORT).show();
								}
							});
							doRetry = true;
							Log.e(Constants.DEBUGTAG, "No Route found.");
						} catch (final UnknownHostException uhe) {
							askRetryForReason(R.string.ddmap_info_route_fetch_no_dataconnection_title, R.string.ddmap_info_route_fetch_no_dataconnection_message);
							return;
						} catch (final Exception e) {
							doRetry = true;
							Log.e(Constants.DEBUGTAG, "Route-Fetch-Error.", e);
						}

						if(OpenStreetDDMap.this.mGPStartInitial == null) {
							OpenStreetDDMap.this.mGPStartInitial = OpenStreetDDMap.this.mGPStart;
						}

						OpenStreetDDMap.this.mRoute = aRoute;

						if (doRetry){
							count++;
							if(count >= MAPFETCH_TRIES_TO_GET_ROUTE) {
								if(OpenStreetDDMap.this.mBundleCreatedWith.getString(EXTRAS_STREETNUMBER_ID) != null) {
									askToTryWithoutStreetnumber();
								} else {
									askRetryForReason(R.string.ddmap_info_route_fetch_tries_exceeded_title, R.string.ddmap_info_route_fetch_tries_exceeded_message);
								}
								return;
							}
						}
					} while (doRetry);

					OpenStreetDDMap.this.mLastWorkingWayPoints = org.androad.util.Util.cloneDeep(OpenStreetDDMap.this.mWayPoints);

					Log.d(Constants.DEBUGTAG, "Route Found!");
					/* Route was found... */
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							initNavigationOnRoute(OpenStreetDDMap.this.mRoute, pRestore);
						}
					});
				}

				/**
				 * Opens an AlterDialog,
				 * which asks the user if he wants to try searching the route,
				 * without the streetnumber he entered.
				 * If he presses <code>YES</code>, the streetNumber gets deleted from the
				 * <code>mBundleCreatedWith</code> and the Route-ReFetch gets kicked of again.
				 * If he presses <code>NO</code> this Activity gets finished.
				 */
				private void askToTryWithoutStreetnumber(){
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							try{
								OpenStreetDDMap.this.mRouteFetchProgressDialog.dismiss();
								final AlertDialog.Builder ab = new AlertDialog.Builder(OpenStreetDDMap.this)
								.setTitle(R.string.ddmap_info_route_fetch_tries_exceeded_ask_remove_streetnumber_title)
								.setIcon(R.drawable.face_sad)
								.setMessage(R.string.ddmap_info_route_fetch_tries_exceeded_ask_remove_streetnumber_message)
								.setPositiveButton(R.string.yes_UPPERCASE, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(final DialogInterface arg0, final int arg1) {
										OpenStreetDDMap.this.mBundleCreatedWith.remove(EXTRAS_STREETNUMBER_ID);
										OpenStreetDDMap.this.kickOffRouteFetch();
									}
								})
								.setNegativeButton(R.string.no_UPPERCASE, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(final DialogInterface arg0, final int arg1) {
										OpenStreetDDMap.this.finish();
									}
								});

								if(OpenStreetDDMap.this.mLastWorkingRoute != null){
									ab.setNeutralButton(R.string.ddmap_info_route_fetch_useold, new DialogInterface.OnClickListener() {
										@Override
										public void onClick(final DialogInterface arg0, final int arg1) {
											OpenStreetDDMap.this.mDoAutomaticRouteRefetch = false;
											OpenStreetDDMap.this.mRoute = OpenStreetDDMap.this.mLastWorkingRoute;
											OpenStreetDDMap.this.mWayPoints = OpenStreetDDMap.this.mLastWorkingWayPoints;
											initNavigationOnRoute(OpenStreetDDMap.this.mRoute, INITDRIVINGDIRECTIONS_NEWROUTE);
										}
									});
								}
								ab.create().show();
							}catch(final IllegalArgumentException iae){
								// Thrown when dismiss is called and activity is already been destroyed
								//							Log.e(DEBUGTAG, "Error", iae);
							}catch(final BadTokenException btw){
								// Thrown when create/show is called and activity is already been destroyed
								//	Log.e(DEBUGTAG, "Error", iae);
							}
						}

					});
				}

				/**
				 * Opens an AlterDialog,
				 * which asks the user if he wants to retry searching for the route,
				 * without message and title passed as parameters.
				 * If he presses <code>YES</code> the Route-ReFetch gets kicked of again.
				 * If he presses <code>NO</code> this Activity gets finished.
				 * @param aTitleResID
				 * @param aMessageResID
				 */
				private void askRetryForReason(final int aTitleResID, final int aMessageResID) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							try{
								OpenStreetDDMap.this.mRouteFetchProgressDialog.dismiss();
								final AlertDialog.Builder ab = new AlertDialog.Builder(OpenStreetDDMap.this)
								.setTitle(aTitleResID)
								.setIcon(R.drawable.face_sad)
								.setMessage(aMessageResID)
								.setPositiveButton(R.string.yes_UPPERCASE, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(final DialogInterface arg0, final int arg1) {
										OpenStreetDDMap.this.kickOffRouteFetch();
									}
								})
								.setNegativeButton(R.string.no_UPPERCASE, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(final DialogInterface arg0, final int arg1) {
										OpenStreetDDMap.this.finish();
									}
								});

								if(OpenStreetDDMap.this.mLastWorkingRoute != null){
									ab.setNeutralButton(R.string.ddmap_info_route_fetch_useold, new DialogInterface.OnClickListener() {
										@Override
										public void onClick(final DialogInterface arg0, final int arg1) {
											OpenStreetDDMap.this.mDoAutomaticRouteRefetch = false;
											OpenStreetDDMap.this.mRoute = OpenStreetDDMap.this.mLastWorkingRoute;
											OpenStreetDDMap.this.mWayPoints = OpenStreetDDMap.this.mLastWorkingWayPoints;
											initNavigationOnRoute(OpenStreetDDMap.this.mRoute, INITDRIVINGDIRECTIONS_NEWROUTE);
										}
									});
								}
								ab.create().show();
							}catch(final IllegalArgumentException iae){
								// Thrown when dismiss is called and activity is already been destroyed
								//	Log.e(DEBUGTAG, "Error", iae);
							}catch(final BadTokenException btw){
								// Thrown when create/show is called and activity is already been destroyed
								//	Log.e(DEBUGTAG, "Error", iae);
							}
						}
					});
				}
			}, "RouteFetch-Thread").start();
		} catch(final BadTokenException bte){
			// Happens somewhen in AdProgressView.show();
		}catch (final Exception e) {
			Exceptor.e("Routefetch Error", e, this);
			//			Log.e(Constants.DEBUGTAG, "Exception in kickOffRouteFetch();", e);
		}
	}

	private void ensureOwnGPSPosition() {
		int tryCount = MAPFETCH_TRIES_TO_GET_GPS;
		do{
			tryCount--;
			/* Refresh own GPS position. */
			if(!OpenStreetDDMap.this.mLocationProvider.hasLastKnownLocation()){
				/* Wait until WPS did the job... */
				try {
					Thread.sleep(200);
				} catch (final InterruptedException e) { }
			}
		}while(!OpenStreetDDMap.this.mLocationProvider.hasLastKnownLocation() && tryCount >= 0);
	}

	/**
	 * Initializes the Navigator to work on the Route passed as parameter.<br/>
	 * The <code>mNavigator</code> field will be working on that Route.
	 * @param aRoute
	 * @param
	 */
	private void initNavigationOnRoute(final Route aRoute, final boolean pRestore) {
		this.mRoute = aRoute;
		try{
			this.findViewById(R.id.iv_ddmap_logo_osm).setVisibility(View.GONE);
			this.refreshHUD();

			if (this.mRoute != null) {

				if(!pRestore){
					this.mStaticNavCurrentTurnIndex = 0;
					this.mStaticNavNextTurnIndex = 1;
				}

				this.mNavigator.setRoute(this.mRoute);
				this.mNavigator.setReady(true);
				this.mRouteRefetchRunning = false;
				this.mNavigator.forceOffRouteListenerUpdateInNextTick();
				this.mNavigator.tick(super.mLocationProvider.getLastKnownLocation());

				this.mOSMapView.postInvalidate();

				if (this.mInitialRouteFetch || pRestore) {
					this.mInitialRouteFetch = false;

					final BoundingBoxE6 bb = this.mRoute.getBoundingBoxE6();

					this.mOSMapView.getController().zoomToSpan(bb);
					this.mOSMapView.getController().setCenter(this.getLastKnownLocationAsGeoPoint(true));
				}

				this.mOSMapView.postInvalidate();
				this.refreshHUD();

				if(this.mRouteFetchProgressDialog != null){
					/* The check is needed, because when the Route was
					 * restored due to a change of the ScreenOrientation,
					 * there was no ProgressDialog created. */
					try{
						OpenStreetDDMap.this.mRouteFetchProgressDialog.dismiss();
					}catch(final IllegalArgumentException iae){ /* Nothing. */ }
				}
			}
		}catch(final Exception e){
			// Thrown when dismiss is called and activity is already been destroyed
			Exceptor.e("Error", e, OpenStreetDDMap.this);
		}
	}

	/**
	 * Blocking method, which returns the Route based on <code>mBundleCreatedWith</code>.
	 * Also saves the route to SD-Card, if its the initial call and when the user saved the specific setting to the preferences.
	 * @return the Route
	 * @throws ORSException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws SAXException
	 * @throws Exception
	 */
	private Route createRouteFromBundleCreatedWith() throws ORSException, IOException, IllegalArgumentException, SAXException, Exception{
		final int searchMode = OpenStreetDDMap.this.mBundleCreatedWith.getInt(EXTRAS_MODE);

		final boolean saveRouteToSDCard = OpenStreetDDMap.this.mInitialRouteFetch  && Preferences.getSaveInitialRoute(this);

		/* Switch on the Mode. */
		switch (searchMode) {
			case EXTRAS_MODE_LOAD_ROUTE_BY_ROUTEHANDLEID:
				final long routeHandleID = this.mBundleCreatedWith.getLong(EXTRAS_ROUTEHANDLEID_ID);
				return RouteFactory.create(this, routeHandleID);
			case EXTRAS_MODE_LOAD_SAVED_ROUTE:
				final String aFileName = this.mBundleCreatedWith.getString(EXTRAS_SAVED_ROUTE_FILENAME_ID);
				return RSOfflineLoader.load(this, aFileName);
			case EXTRAS_MODE_DIRECT_LATLNG:
				if(this.mGPDestination == null){
					final int latE6 = this.mBundleCreatedWith.getInt(EXTRAS_DESTINATION_LATITUDE_ID);
					final int lonE6 = this.mBundleCreatedWith.getInt(EXTRAS_DESTINATION_LONGITUDE_ID);
					this.mGPDestination = new GeoPoint(latE6, lonE6);
				}

				if(this.mInitialRouteFetch){
					this.mWayPoints.clear();
					final ArrayList<String> viaStrings = this.mBundleCreatedWith.getStringArrayList(EXTRAS_VIAS_ID);
					if(viaStrings != null) {
						for (final String v : viaStrings) {
							this.mWayPoints.add(GeoPoint.fromIntString(v));
						}
					}
				}


				final int startLatE6 = this.mBundleCreatedWith.getInt(EXTRAS_START_LATITUDE_ID);
				final int startLonE6 = this.mBundleCreatedWith.getInt(EXTRAS_START_LONGITUDE_ID);
				if(startLatE6 == 0 && startLonE6 == 0){
					/* No startPoint was passed. */
					return RouteFactory.create(this, this.mGPStart, this.mGPDestination, this.mWayPoints, this.mAvoidAreas, saveRouteToSDCard);
				}else{
					if(this.mGPStartInitial == null){
						this.mGPStartInitial = new GeoPoint(startLatE6, startLonE6);
						return RouteFactory.create(this, this.mGPStartInitial, this.mGPDestination, this.mWayPoints, this.mAvoidAreas, saveRouteToSDCard);
					}else{
						return RouteFactory.create(this, this.mGPStart, this.mGPDestination, this.mWayPoints, this.mAvoidAreas, saveRouteToSDCard);
					}
				}
			case EXTRAS_MODE_ZIPSEARCH:
			case EXTRAS_MODE_CITYNAMESEARCH:
			default:
				throw new IllegalStateException("Unknown MODE in initDestination.");
		}
	}

	/**
	 * Sets the title of the <code>mRouteFetchProgressDialog</code>.
	 * @param aTitleStringID like <code>R.string.hello_world</code>
	 */
	private void setProgressDialogTitle(final int aTitleStringID) {
		setProgressDialogTitle(getString(aTitleStringID));
	}

	/**
	 * Sets the title of the <code>mRouteFetchProgressDialog</code>.
	 * @param aTitle like <code>"Hello World"</code>
	 */
	private void setProgressDialogTitle(final String aTitle) {
		runOnUiThread(new Runnable(){
			@Override
			public void run() {
				try{
					OpenStreetDDMap.this.mRouteFetchProgressDialog.setTitle(aTitle);
				}catch(final Exception e){
					//					Log.e(DEBUGTAG, "Error", e);
				}
			}
		});
	}

	/**
	 * Returns a string describing the mode the route was requested. <br/>
	 * Examples
	 * <ul><li>"Loading from route: "</li><li>"Latitude: 123.45456\nLongitude: 123.4567"</li><li>"Loadign saved route..."</li></ul>
	 * @return
	 */
	private String getDialogMessage() {
		final StringBuilder sb = new StringBuilder();

		/* Switch on the Mode. */
		final int searchMode = OpenStreetDDMap.this.mBundleCreatedWith.getInt(EXTRAS_MODE);
		switch (searchMode) {
			case EXTRAS_MODE_LOAD_ROUTE_BY_ROUTEHANDLEID:
				sb.append(getString(R.string.pdg_fetchroute_loading_routebyhandle, this.mBundleCreatedWith.getLong(EXTRAS_ROUTEHANDLEID_ID)));
				break;
			case EXTRAS_MODE_LOAD_SAVED_ROUTE:
				sb.append(getString(R.string.pdg_fetchroute_loading_saved_route));
				break;
			case EXTRAS_MODE_DIRECT_LATLNG:
				final DecimalFormat df = new DecimalFormat("##0.000000");

				sb.append(getString(R.string.latitude));
				sb.append(": ");
				sb.append(df.format(this.mBundleCreatedWith.getInt(EXTRAS_DESTINATION_LATITUDE_ID) / 1E6));
				sb.append('\n');
				sb.append(getString(R.string.longitude));
				sb.append(": ");
				sb.append(df.format(this.mBundleCreatedWith.getInt(EXTRAS_DESTINATION_LONGITUDE_ID) / 1E6));
				break;
		}
		return sb.toString();
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class StaticNavOverlayControlView implements ItemizedOverlayControlView.ItemizedOverlayControlViewListener{
		@Override
		public void onCenter() {
			if(OpenStreetDDMap.this.mRoute != null){
				final List<RouteInstruction> turnPointsRaw = OpenStreetDDMap.this.mRoute.getRouteInstructions();
				if(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex == turnPointsRaw.size() - 1){
					OpenStreetDDMap.this.mOSMapView.getController().animateTo(OpenStreetDDMap.this.mRoute.getDestination(), AnimationType.MIDDLEPEAKSPEED);
				}else{
					OpenStreetDDMap.this.mOSMapView.getController().animateTo(turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getTurnPoint(), AnimationType.MIDDLEPEAKSPEED);
				}
			}
			OpenStreetDDMap.this.mOSMapView.invalidate();
			OpenStreetDDMap.this.refreshHUD();

			if(OpenStreetDDMap.this.mDirectionVoiceEnabled){
				final String turnDescription = OpenStreetDDMap.this.mRoute.getRouteInstructions().get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getDescription();
				OpenStreetDDMap.this.mTTS.speak(SpeechImprover.improve(turnDescription, OpenStreetDDMap.this.mRouteCountry), 0, null);
			}
		}

		@Override
		public void onNavTo() {
			// Nothing, is disabled so it will never happen.
		}

		@Override
		public void onNext() {
			if(OpenStreetDDMap.this.mRoute != null){
				final List<RouteInstruction> turnPointsRaw = OpenStreetDDMap.this.mRoute.getRouteInstructions();
				OpenStreetDDMap.this.mStaticNavCurrentTurnIndex = Math.min(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex + 1, turnPointsRaw.size() - 1);
				OpenStreetDDMap.this.mStaticNavNextTurnIndex = Math.min(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex + 1, turnPointsRaw.size() - 1);

				if(OpenStreetDDMap.this.mDirectionVoiceEnabled){
					final RouteInstruction currentInstruction = OpenStreetDDMap.this.mRoute.getRouteInstructions().get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex);
					final RouteInstruction nextInstruction = OpenStreetDDMap.this.mRoute.getRouteInstructions().get(OpenStreetDDMap.this.mStaticNavNextTurnIndex);
					final UnitSystem us = Preferences.getUnitSystem(OpenStreetDDMap.this);
					final String[] lengthAndUnit = us.getDistanceStringFull(OpenStreetDDMap.this, OpenStreetDDMap.this.mDrivingDirectionsLanguage, null, currentInstruction.getLengthMeters());
					final String turnDescription = "In " + lengthAndUnit[UnitSystem.DISTSTRINGS_DIST_ID] + " " + lengthAndUnit[UnitSystem.DISTSTRINGS_UNIT_ID] + ", " + nextInstruction.getDescription();

					OpenStreetDDMap.this.mTTS.speak(SpeechImprover.improve(turnDescription, OpenStreetDDMap.this.mRouteCountry), 0, null);
				}

				if(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex == turnPointsRaw.size() - 1){
					OpenStreetDDMap.this.mOSMapView.getController().animateTo(OpenStreetDDMap.this.mRoute.getDestination(), AnimationType.MIDDLEPEAKSPEED);
				}else{
					switch(OpenStreetDDMap.this.mCenterMode){
						case PREF_CENTERMODE_CENTERUSER:
							OpenStreetDDMap.this.mOSMapView.getController().animateTo(turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getTurnPoint(), AnimationType.MIDDLEPEAKSPEED);
							break;
						case PREF_CENTERMODE_UPTO_NEXTTURN:
							final int curIndex = Math.min(OpenStreetDDMap.this.mRoute.getPolyLine().size() - 1,
									turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex).getFirstMotherPolylineIndex() + 1);
							final GeoPoint curTP = turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex).getTurnPoint();
							final GeoPoint nextTP = turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getTurnPoint();
							centerAndZoomBetween(curIndex,curTP, nextTP, true);
							break;
					}
				}
			}
			OpenStreetDDMap.this.mOSMapView.invalidate();
			OpenStreetDDMap.this.refreshHUD();
		}

		@Override
		public void onPrevious() {
			if(OpenStreetDDMap.this.mRoute != null){
				OpenStreetDDMap.this.mStaticNavCurrentTurnIndex = Math.max(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex - 1, 0);
				final List<RouteInstruction> turnPointsRaw = OpenStreetDDMap.this.mRoute.getRouteInstructions();
				OpenStreetDDMap.this.mStaticNavNextTurnIndex = Math.min(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex + 1, turnPointsRaw.size() - 1);
				switch(OpenStreetDDMap.this.mCenterMode){
					case PREF_CENTERMODE_CENTERUSER:
						OpenStreetDDMap.this.mOSMapView.getController().animateTo(turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getTurnPoint(), AnimationType.MIDDLEPEAKSPEED);
						break;
					case PREF_CENTERMODE_UPTO_NEXTTURN:
						final int curIndex = Math.min(OpenStreetDDMap.this.mRoute.getPolyLine().size() - 1,
								turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex).getFirstMotherPolylineIndex() + 1);
						final GeoPoint curTP = turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavCurrentTurnIndex).getTurnPoint();
						final GeoPoint nextTP = turnPointsRaw.get(OpenStreetDDMap.this.mStaticNavNextTurnIndex).getTurnPoint();
						centerAndZoomBetween(curIndex,curTP, nextTP, true);
						break;
				}
			}
			OpenStreetDDMap.this.mOSMapView.invalidate();
			OpenStreetDDMap.this.refreshHUD();
		}
	}
}
