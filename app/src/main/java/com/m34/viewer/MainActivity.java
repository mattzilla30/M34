package com.m34.viewer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    static { System.loadLibrary("m34"); }
    private static native int nativeApi();
    Library library;
    Remote remote;
    LinearLayout root, body, navigation, gridHost, suggestionHost;
    final SearchSession searchSession=new SearchSession();
    String loadedSearchKey="";
    Runnable pendingSearch;
    final Set<String> activeDownloads=new HashSet<>();
    Dialog artworkDialog;
    ScrollView scroll;
    int tab=0, page=0, generation=0, galleryScrollY=0;
    String autoPlayedId="";
    Remote.PostPage lastResponse;
    String query="", savedQuery="", order="Latest", selectedCollection="All saved";
    Catalog.Art detail;
    java.util.List<Catalog.Art> livePosts=new ArrayList<>();
    boolean loading=false, liveEnded=false, unlocked=false, unlocking=false;
    int bg, surface, text, muted, line, accent;
    final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n+.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(nativeApi()!=37) { finish();return; }
        library=new Library(this);remote=new Remote(getCacheDir());
        library.prefs.edit().remove("source").remove("liveSetupV103").apply();
        if(state!=null) {galleryScrollY=state.getInt("galleryScrollY",0);tab=state.getInt("tab");query=state.getString("query","");savedQuery=state.getString("savedQuery","");order=state.getString("order","Latest");selectedCollection=state.getString("collection","All saved");try {if(state.containsKey("detail")) detail=Library.decode(new org.json.JSONObject(state.getString("detail")));}catch(Exception ignored){} }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{
            if(detail!=null) {closeDetail();}
            else if(tab!=0) {tab=0;render();}
            else finish();
        });
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);out.putInt("galleryScrollY",galleryScrollY);out.putInt("tab",tab);out.putString("query",query);out.putString("savedQuery",savedQuery);out.putString("order",order);out.putString("collection",selectedCollection);
        if(detail!=null)try{out.putString("detail",Library.encode(detail).toString());}catch(Exception ignored){}
    }
    @Override protected void onStart() {super.onStart();if(library!=null&&library.flag("lock",false)&&!unlocked&&!unlocking) authenticate();}
    @Override protected void onStop() {super.onStop();if(!unlocking){unlocked=false;if(library.flag("lock",false)&&artworkDialog!=null)artworkDialog.dismiss();if(library.flag("lock",false)) {body.removeAllViews();body.addView(label("M34 is locked",24,true));navigation.setVisibility(View.GONE);}}}
    @Override protected void onDestroy() {super.onDestroy();generation++;remote.close();handler.removeCallbacksAndMessages(null);}
    void authenticate() {
        KeyguardManager km=getSystemService(KeyguardManager.class);
        Intent intent=km.createConfirmDeviceCredentialIntent("Unlock M34","Use your device screen lock");
        if(intent==null) {library.putFlag("lock",false);toast("Set a device screen lock before enabling app lock");render();return;}
        unlocking=true;body.removeAllViews();navigation.setVisibility(View.GONE);startActivityForResult(intent,41);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==41) {unlocking=false;if(result==RESULT_OK){unlocked=true;render();}else finish();}
    }
    void colors() {
        boolean light=library.value("theme","Dark").equals("Light");
        bg=Color.parseColor(light?"#F3F5F0":"#101214");surface=Color.parseColor(light?"#FFFFFF":"#222629");
        text=Color.parseColor(light?"#182019":"#F1F3EF");muted=Color.parseColor(light?"#5A665D":"#A2AAA5");line=Color.parseColor(light?"#DAE0D7":"#353C3B");
        accent=Color.parseColor(library.value("accent","#B2FF72"));
    }
    GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),line);return d; }
    TextView label(String value,int size,boolean bold) { TextView t=new TextView(this);t.setText(value);t.setTextColor(text);t.setTextSize(size);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,1);return t; }
    TextView sub(String value) {TextView t=label(value,12,false);t.setTextColor(muted);return t;}
    LinearLayout vertical() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout horizontal() {LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void space(LinearLayout l,int h) {View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    void grow(LinearLayout l,View v) {l.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));}
    TextView button(String title,boolean active,Runnable action) {
        TextView b=label(title,13,true);b.setGravity(Gravity.CENTER);b.setTextColor(active?0xff152010:text);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setMinHeight(dp(48));b.setBackground(shape(active?accent:surface,24));b.setOnClickListener(v->action.run());return b;
    }
    void chip(LinearLayout l,String title,boolean active,Runnable action) {TextView b=button(title,active,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginEnd(dp(8));l.addView(b,p);}
    void strip(LinearLayout target,LinearLayout row) {HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);sc.addView(row);target.addView(sc,new LinearLayout.LayoutParams(-1,-2));}
    void toast(String t) {Toast.makeText(this,t,Toast.LENGTH_SHORT).show();}
    void render() {
        if(pendingSearch!=null){handler.removeCallbacks(pendingSearch);pendingSearch=null;}suggestionHost=null;generation++;loading=false;colors();if(library.flag("lock",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);root=vertical();root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets a=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());root.setPadding(a.left,a.top,a.right,a.bottom);return insets;});
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=vertical();body.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=horizontal();navigation.setPadding(dp(8),dp(5),dp(8),dp(5));navigation.setBackgroundColor(bg);
        String[] names={"Explore","Search","Saved","Settings"};
        for(int i=0;i<4;i++) {final int index=i;LinearLayout nav=vertical();nav.setGravity(Gravity.CENTER);nav.setPadding(0,dp(5),0,dp(5));nav.setContentDescription(names[i]);Icon icon=new Icon(i,tab==i?accent:muted);nav.addView(icon,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView caption=label(names[i],11,tab==i);caption.setTextColor(tab==i?accent:muted);caption.setGravity(Gravity.CENTER);nav.addView(caption);nav.setMinimumHeight(dp(55));nav.setOnClickListener(v->{tab=index;detail=null;render();});navigation.addView(nav,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(navigation);
        SystemBars.bind(root,library.value("theme","Dark").equals("Light"));
        setContentView(root);root.requestApplyInsets();
        if(detail!=null) details(); else if(tab==3) settings();else if(tab==2) saved();else browse();
    }
    void header(String subtitle) {
        LinearLayout row=horizontal();TextView logo=label("M34",34,true);logo.setTextColor(accent);grow(row,logo);body.addView(row);
        if(!subtitle.isEmpty()){space(body,6);body.addView(sub(subtitle));}space(body,18);
    }
    EditText edit(String hint,String value) {
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(text);e.setHintTextColor(muted);e.setHint(hint);e.setText(value);e.setPadding(dp(16),dp(12),dp(16),dp(12));e.setBackground(shape(surface,20));e.setMinHeight(dp(52));e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);return e;
    }
    void searchBox(boolean saved) {
        LinearLayout row=horizontal();EditText field=edit(saved?"Search saved artwork…":"Search tags…",saved?savedQuery:query);grow(row,field);
        Runnable submit=()->{
            if(pendingSearch!=null){handler.removeCallbacks(pendingSearch);pendingSearch=null;}
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);field.clearFocus();
            if(saved){savedQuery=field.getText().toString().trim();render();}else submitSearch(field.getText().toString(),true);
        };
        TextView go=button("Go",true,submit);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginStart(dp(8));row.addView(go,p);
        TextView clear=button("×",false,()->{if(saved){savedQuery="";render();}else submitSearch("",true);});clear.setContentDescription(saved?"Clear saved search":"Clear search");clear.setVisibility(field.getText().toString().isBlank()?View.GONE:View.VISIBLE);row.addView(clear);
        field.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){clear.setVisibility(value.toString().isBlank()?View.GONE:View.VISIBLE);}
            public void afterTextChanged(Editable value){}
        });
        body.addView(row);
        field.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){submit.run();return true;}return false;});
        if(!saved){View owner=root;int ownerTab=tab;field.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){
                if(pendingSearch!=null)handler.removeCallbacks(pendingSearch);String draft=value.toString();query=SearchTags.normalize(draft);
                pendingSearch=()->{if(root==owner&&tab==ownerTab&&detail==null){pendingSearch=null;submitSearch(draft,false);}};
                handler.postDelayed(pendingSearch,700);
            }
            public void afterTextChanged(Editable value){}
        });}

    }
    void browse() {
        header("Rule34 · "+(library.flag("safe",true)?"Safe & general":"All ratings"));
        searchBox(false);space(body,16);
        if(tab==1) {suggestionHost=vertical();body.addView(suggestionHost);renderSuggestions();space(body,16);}
        LinearLayout filters=horizontal();for(String s:new String[]{"Trending","Latest","Top rated"})chip(filters,s,order.equals(s),()->{order=s;page=0;liveEnded=false;livePosts.clear();render();});strip(body,filters);space(body,12);
        LinearLayout tools=horizontal();grow(tools,sub(library.flag("safe",true)?"Safe & general only":"All ratings enabled"));tools.addView(button("Refresh",false,()->{livePosts.clear();page=0;liveEnded=false;lastResponse=null;render();}));tools.addView(button("Filters",false,this::filters));body.addView(tools);space(body,14);
        gridHost=vertical();body.addView(gridHost);
        if(!loadedSearchKey.equals(currentSearchKey())){livePosts.clear();page=0;liveEnded=false;lastResponse=null;}
        if(livePosts.isEmpty())loadPosts(false);else renderLiveGrid();
    }
    String currentSearchKey(){return SearchSession.key(query,library.flag("safe",true),order);}
    void submitSearch(String value,boolean rebuild){
        query=SearchTags.normalize(value);galleryScrollY=0;library.history(query);livePosts.clear();lastResponse=null;loadedSearchKey="";page=0;liveEnded=false;loading=false;
        if(rebuild)render();else{renderSuggestions();loadPosts(false);}
    }
    void searchTag(String tag){tab=1;detail=null;submitSearch(tag,true);}
    int tagRefresh=0;
    java.util.List<String> websiteTags=new ArrayList<>();
    void renderSuggestions(){
        if(suggestionHost==null)return;LinearLayout host=suggestionHost;host.removeAllViews();int request=++tagRefresh;
        LinearLayout heading=horizontal();grow(heading,sub("DISCOVER TAGS"));heading.addView(button("Refresh",false,this::renderSuggestions));host.addView(heading);
        TextView status=sub("Getting random tags from Rule34…");host.addView(status);
        String key;try{key=library.apiKey();}catch(Exception e){status.setText("Re-enter your API credentials to load tags.");return;}
        if(key.isEmpty()){status.setText("Connect your account to discover tags.");return;}
        remote.randomTags(library.flag("safe",true),library.value("userId",""),key,websiteTags,new Remote.Result<java.util.List<String>>(){
            public void success(java.util.List<String> tags){if(isDestroyed()||suggestionHost!=host||tagRefresh!=request)return;websiteTags=tags;host.removeView(status);LinearLayout row=horizontal();for(String tag:tags)chip(row,tag,false,()->searchTag(tag));if(tags.isEmpty())host.addView(sub("No tags returned. Tap Refresh to try again."));else strip(host,row);}
            public void failure(String message){if(isDestroyed()||suggestionHost!=host||tagRefresh!=request)return;status.setText(message);}
        });
    }
    void filters() {
        LinearLayout box=vertical();box.setPadding(dp(20),dp(8),dp(20),dp(8));Switch safe=new Switch(this);safe.setText("Safe only");safe.setTextColor(text);safe.setChecked(library.flag("safe",true));safe.setMinHeight(dp(56));box.addView(safe);box.addView(sub("Tags support spaces and exclusions, e.g.\nlandscape -night"));
        new AlertDialog.Builder(this).setTitle("Browsing filters").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{library.putFlag("safe",safe.isChecked());livePosts.clear();page=0;liveEnded=false;render();}).show();
    }
    void loadPosts(boolean more) {
        if(loading)return;loading=true;int token=generation;int next=more?page+1:0;
        final String requestedQuery=query,requestedOrder=order;final boolean requestedSafe=library.flag("safe",true);
        final String requestedKey=SearchSession.key(requestedQuery,requestedSafe,requestedOrder);final int revision=searchSession.begin(requestedKey);
        if(!more)gridHost.removeAllViews();
        if(library.value("apiKey","").isEmpty()||library.value("userId","").isEmpty()){loading=false;empty(gridHost,"Connect your account","Add your Rule34 API credentials to browse and search.");gridHost.addView(button("Add API credentials",true,this::credentials));return;}
        TextView status=sub("Loading artwork…");gridHost.addView(status);
        String key;try{key=library.apiKey();}catch(Exception e){loading=false;gridHost.removeView(status);empty(gridHost,"Credentials unavailable","Re-enter your API key in Settings.");return;}
        remote.posts(requestedQuery,requestedSafe,requestedOrder,next,library.value("userId",""),key,new Remote.Result<Remote.PostPage>(){
            public void success(Remote.PostPage response){java.util.List<Catalog.Art> arts=response.items;if(token!=generation||isDestroyed()||!searchSession.accepts(revision,requestedKey)||!requestedKey.equals(currentSearchKey()))return;loading=false;page=next;lastResponse=response;loadedSearchKey=requestedKey;if(!more)livePosts.clear();Set<String> ids=new HashSet<>();for(Catalog.Art a:livePosts)ids.add(a.id);for(Catalog.Art a:arts)if(ids.add(a.id))livePosts.add(a);liveEnded=response.received<30;if(livePosts.size()>90)livePosts=new ArrayList<>(livePosts.subList(livePosts.size()-90,livePosts.size()));renderLiveGrid();}
            public void failure(String message){if(token!=generation||isDestroyed()||!searchSession.accepts(revision,requestedKey)||!requestedKey.equals(currentSearchKey()))return;loading=false;gridHost.removeView(status);empty(gridHost,"Couldn’t load artwork",message);gridHost.addView(button("Retry",true,()->{gridHost.removeAllViews();if(more)renderLiveGrid();loadPosts(more);}));space(gridHost,8);gridHost.addView(button("Edit API credentials",false,MainActivity.this::credentials));}
        });
    }
    void renderLiveGrid() {gridHost.removeAllViews();gridHost.addView(sub((query.isBlank()?"Latest artwork":"Tags: "+query)+" · "+livePosts.size()+" posts · Page "+(page+1)));space(gridHost,12);if(livePosts.isEmpty()){empty(gridHost,lastResponse==null?"No results loaded":lastResponse.emptyTitle(),lastResponse==null?"Try your search again.":lastResponse.summary());gridHost.addView(button("Adjust filters",false,this::filters));space(gridHost,8);gridHost.addView(button("Reset search",true,()->{query="";order="Latest";page=0;liveEnded=false;livePosts.clear();render();}));}else grid(gridHost,livePosts);space(gridHost,16);if(!liveEnded)gridHost.addView(button("Load more",false,()->loadPosts(true)));}
    void empty(LinearLayout target,String title,String subtitle) {space(target,35);target.addView(label(title,21,true));space(target,8);target.addView(sub(subtitle));space(target,22);}
    void grid(LinearLayout target,java.util.List<Catalog.Art> items) {
        if(items.isEmpty()){empty(target,"Nothing here yet","Try different tags, adjust filters, or save artwork from Explore.");return;}
        int count=Integer.parseInt(library.value("columns","2"));
        LinearLayout columns=horizontal();columns.setGravity(Gravity.TOP);LinearLayout[] col=new LinearLayout[count];
        for(int i=0;i<count;i++){col[i]=vertical();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i<count-1)p.setMarginEnd(dp(10));columns.addView(col[i],p);}
        for(int i=0;i<items.size();i++)col[i%count].addView(card(items.get(i),i,count));target.addView(columns);
    }
    View card(Catalog.Art a,int index,int count) {
        LinearLayout card=vertical();card.setBackground(shape(surface,14));card.setClipToOutline(true);card.setPadding(0,0,0,dp(8));
        FrameLayout frame=new FrameLayout(this);ArtView art=new ArtView(this,a);int height=count==1?310:count==3?155:205+(index%3)*22;
        frame.addView(art,new FrameLayout.LayoutParams(-1,dp(height)));art.setOnClickListener(v->open(a));loadImage(art);
        if(a.live()){TextView attribution=sub("Rule34 · "+a.rating.toUpperCase(Locale.ROOT));attribution.setPadding(dp(8),dp(4),dp(8),dp(4));attribution.setBackgroundColor(0xaa101214);FrameLayout.LayoutParams at=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);frame.addView(attribution,at);}
        TextView save=button(library.saved.containsKey(a.id)?"▣":"＋",library.saved.containsKey(a.id),()->{boolean added=library.toggleSave(a);toast(added?"Saved to your library":"Removed from saved");if(tab==2)render();else {TextView b=(TextView)frame.getChildAt(frame.getChildCount()-1);b.setText(added?"▣":"＋");b.setBackground(shape(added?accent:surface,24));b.setTextColor(added?0xff152010:text);}});
        save.setPadding(dp(8),0,dp(8),0);save.setContentDescription("Save or unsave "+a.title);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.END);sp.setMargins(0,dp(6),dp(6),0);frame.addView(save,sp);card.addView(frame);
        TextView title=label(a.title,13,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(dp(10),dp(8),dp(8),0);card.addView(title);
        if(library.flag("details",true)){TextView info=sub("♡ "+String.format(Locale.US,"%,d",a.score)+"  ·  "+a.artist);info.setMaxLines(1);info.setPadding(dp(10),dp(3),dp(6),0);card.addView(info);}
        card.setOnClickListener(v->open(a));LinearLayout outer=vertical();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);outer.addView(card,p);return outer;
    }
    void loadImage(ArtView art) {
        if(!art.art.live())return;art.setAlpha(.55f);remote.image(art.art.preview,new Remote.Result<Bitmap>(){
            public void success(Bitmap b){if(isDestroyed())return;art.image=b;art.setAlpha(1);art.invalidate();}
            public void failure(String message){art.setAlpha(1);art.previewStatus="Preview unavailable";art.invalidate();art.setContentDescription(art.art.title+" · "+message);}
        });
    }
    void open(Catalog.Art a){galleryScrollY=scroll.getScrollY();autoPlayedId="";detail=a;render();}
    void closeDetail(){detail=null;autoPlayedId="";render();int y=galleryScrollY;scroll.post(()->scroll.scrollTo(0,y));}
    void saved() {
        header("Your personal corner of the internet");LinearLayout libraryHeading=horizontal();grow(libraryHeading,label("Saved",27,true));libraryHeading.addView(button("+ Collection",false,this::newCollection));body.addView(libraryHeading);space(body,16);searchBox(true);space(body,14);
        LinearLayout tabs=horizontal();chip(tabs,"All saved",selectedCollection.equals("All saved"),()->{selectedCollection="All saved";render();});for(String c:library.collections.keySet())chip(tabs,c,selectedCollection.equals(c),()->{selectedCollection=c;render();});strip(body,tabs);space(body,18);
        if(!selectedCollection.equals("All saved")){LinearLayout actions=horizontal();grow(actions,label(selectedCollection,18,true));actions.addView(button("Rename",false,()->editCollection(selectedCollection)));actions.addView(button("Delete",false,()->new AlertDialog.Builder(this).setTitle("Delete collection?").setMessage("Saved artwork will stay in your library.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{library.collections.remove(selectedCollection);library.persist();selectedCollection="All saved";render();}).show()));body.addView(actions);space(body,10);}
        java.util.List<Catalog.Art> items=new ArrayList<>(library.saved.values());Collections.reverse(items);
        if(!selectedCollection.equals("All saved")){Set<String> ids=library.collections.get(selectedCollection);if(ids==null){selectedCollection="All saved";}else items.removeIf(a->!ids.contains(a.id));}
        items.removeIf(a->!Catalog.matches(a,savedQuery)||(library.flag("safe",true)&&!Catalog.isSafe(a.rating)));
        if(library.value("savedOrder","Date saved").equals("Title"))items.sort(Comparator.comparing(a->a.title));
        LinearLayout row=horizontal();grow(row,sub(items.size()+" artworks"));row.addView(button(library.value("savedOrder","Date saved")+" ↕",false,()->choose("Sort saved artwork",new String[]{"Date saved","Title"},library.value("savedOrder","Date saved"),s->{library.putValue("savedOrder",s);render();})));body.addView(row);space(body,14);grid(body,items);
    }
    void newCollection(){editCollection(null);}
    void editCollection(String original){
        EditText field=edit("Collection name",original==null?"":original);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"New collection":"Rename collection").setView(field).setNegativeButton("Cancel",null).setPositiveButton(original==null?"Create":"Rename",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=field.getText().toString().trim();String error=CollectionNames.error(name,original,library.collections.keySet());
            if(error!=null){field.setError(error);return;}
            if(original==null)library.collections.put(name,new LinkedHashSet<>());
            else {LinkedHashMap<String,Set<String>> renamed=new LinkedHashMap<>();for(var entry:library.collections.entrySet())renamed.put(entry.getKey().equals(original)?name:entry.getKey(),entry.getValue());library.collections.clear();library.collections.putAll(renamed);}
            library.persist();dialog.dismiss();
            if(detail==null){selectedCollection=name;render();}else collections(detail);
        }));dialog.show();
    }
    void collections(Catalog.Art a) {
        String[] names=library.collections.keySet().toArray(new String[0]);boolean[] checked=new boolean[names.length];for(int i=0;i<names.length;i++)checked[i]=library.collections.get(names[i]).contains(a.id);
        new AlertDialog.Builder(this).setTitle("Add to collection").setMultiChoiceItems(names,checked,(d,which,value)->{library.setCollection(a,names[which],value);}).setNeutralButton("New collection",(d,w)->newCollection()).setPositiveButton("Done",(d,w)->render()).show();
    }
    void details() {
        Catalog.Art a=detail;LinearLayout top=horizontal();top.addView(button("‹ Back",false,this::closeDetail));grow(top,new View(this));top.addView(button("⋯",false,()->new AlertDialog.Builder(this).setItems(new String[]{"Add to collection","Share","Open source","Copy tags"},(d,w)->{if(w==0)collections(a);else if(w==1)share(a);else if(w==3){getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Artwork tags",a.tags));toast("Tags copied");}else if(a.live())external("https://rule34.xxx/index.php?page=post&s=view&id="+a.id.substring(4));else toast("Saved local artwork");}).show()));body.addView(top);space(body,16);
        ArtView art=new ArtView(this,a);art.setBackground(shape(surface,18));art.setClipToOutline(true);body.addView(art,new LinearLayout.LayoutParams(-1,dp(390)));loadImage(art);art.setOnClickListener(v->zoom(a));space(body,8);body.addView(sub("Tap for fullscreen · Double-tap to zoom"));space(body,18);
        body.addView(label(a.title,29,true));space(body,6);body.addView(sub(a.artist+"  ·  "+(a.live()?"Rule34 post":"Saved local collection")));space(body,18);
        LinearLayout actions=horizontal();chip(actions,library.likes.contains(a.id)?"♥ Liked":"♡ Like",library.likes.contains(a.id),()->{if(!library.likes.remove(a.id))library.likes.add(a.id);library.persist();render();});chip(actions,library.saved.containsKey(a.id)?"Saved":"Save",library.saved.containsKey(a.id),()->{library.toggleSave(a);render();});chip(actions,"Download",false,()->download(a,false));strip(body,actions);space(body,10);
        LinearLayout more=horizontal();chip(more,"Collections",false,()->collections(a));chip(more,"Share",false,()->share(a));strip(body,more);
        if(a.original.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm)(\\?.*)?")){space(body,12);body.addView(button("Play video",true,()->video(a)));if(library.flag("autoplay",false)&&!autoPlayedId.equals(a.id)){autoPlayedId=a.id;handler.post(()->{if(detail==a&&!isDestroyed())video(a);});}}
        space(body,24);body.addView(label("Tags",17,true));space(body,12);LinearLayout tags=horizontal();int n=0;for(String tag:a.tags.split(" ")){if(tag.isEmpty())continue;if(n>0&&n%3==0){strip(body,tags);space(body,8);tags=horizontal();}chip(tags,tag.replace('_',' '),false,()->searchTag(tag));if(++n>=24)break;}strip(body,tags);space(body,10);body.addView(button("All tags ("+a.tags.trim().split("\\s+").length+")",false,()->{String[] all=a.tags.trim().split("\\s+");new AlertDialog.Builder(this).setTitle("All artwork tags").setItems(all,(dialog,index)->searchTag(all[index])).setNegativeButton("Close",null).show();}));space(body,20);body.addView(sub(a.width+" × "+a.height+"  ·  "+(a.live()?"Rating: "+a.rating:"Offline artwork · PNG export")));
    }
    void zoom(Catalog.Art a) {
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_NoActionBar);artworkDialog=dialog;
        if(library.flag("lock",false))dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.setOnDismissListener(x->{if(artworkDialog==dialog)artworkDialog=null;});
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xff080a0c);
        ArtView art=new ArtView(this,a);art.zoomable=true;frame.addView(art,new FrameLayout.LayoutParams(-1,-1));loadImage(art);
        TextView close=button("✕ Close",false,dialog::dismiss);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);cp.setMargins(dp(16),dp(32),dp(16),0);frame.addView(close,cp);
        art.setOnClickListener(v->close.setVisibility(close.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        dialog.setContentView(frame);dialog.setOnShowListener(x->{dialog.getWindow().setLayout(-1,-1);WindowInsetsController controls=dialog.getWindow().getDecorView().getWindowInsetsController();if(controls!=null){controls.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controls.hide(WindowInsets.Type.systemBars());}});dialog.show();
    }
    void video(Catalog.Art a){Dialog d=new Dialog(this);LinearLayout wrap=vertical();VideoView v=new VideoView(this);wrap.addView(v,new LinearLayout.LayoutParams(dp(310),dp(400)));MediaController controller=new MediaController(this);controller.setAnchorView(v);v.setMediaController(controller);v.setVideoURI(Uri.parse(a.original));v.setOnPreparedListener(p->v.start());v.setOnErrorListener((p,w,e)->{toast("This video could not be played");return true;});wrap.addView(button("Close",false,d::dismiss));d.setContentView(wrap);d.setOnDismissListener(x->v.stopPlayback());d.show();}
    void share(Catalog.Art a){if(a.live()){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://rule34.xxx/index.php?page=post&s=view&id="+a.id.substring(4));startActivity(Intent.createChooser(i,"Share artwork"));}else download(a,true);}
    void download(Catalog.Art a,boolean share) {
        String downloadKey=a.id+":"+share;if(!activeDownloads.add(downloadKey)){toast("This download is already in progress");return;}
        toast(share?"Preparing artwork…":"Saving to Downloads…");boolean preview=library.value("quality","Original").equals("Preview");
        ArtView exporter=a.live()?null:new ArtView(this,a);
        remote.transfers.execute(()->{
            Uri uri=null;
            try {
                String url=preview?a.preview:a.original;String ext=a.live()?extension(url):"png";String mime=switch(ext){case "jpg","jpeg"->"image/jpeg";case "webp"->"image/webp";case "gif"->"image/gif";case "mp4"->"video/mp4";case "webm"->"video/webm";default->"image/png";};
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"M34-"+a.id+"-"+System.currentTimeMillis()+"."+ext);values.put(MediaStore.MediaColumns.MIME_TYPE,mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/M34");values.put(MediaStore.MediaColumns.IS_PENDING,1);
                uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Could not create download");
                try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                    if(a.live()){javax.net.ssl.HttpsURLConnection connection=Remote.connect(url);try{if(connection.getResponseCode()!=200)throw new IOException("Download server unavailable");try(InputStream in=connection.getInputStream()){byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException("File exceeds 256 MB prototype limit");out.write(b,0,n);}}}finally{connection.disconnect();}}
                    else {Bitmap bitmap=Bitmap.createBitmap(1600,2000,Bitmap.Config.ARGB_8888);exporter.drawLandscape(new Canvas(bitmap),1600,2000,a.seed);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}
                }
                values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,values,null,null);Uri result=uri;
                handler.post(()->{activeDownloads.remove(downloadKey);if(isDestroyed())return;if(share){Intent intent=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,result).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(intent,"Share artwork"));}else toast("Saved to Downloads/M34");});
            }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);handler.post(()->{activeDownloads.remove(downloadKey);toast("Download failed. Check your connection and available storage.");});}
        });
    }
    String extension(String url){String path=Uri.parse(url).getPath();if(path==null)return "png";String ext=path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);return Arrays.asList("png","jpg","jpeg","webp","gif","mp4","webm").contains(ext)?ext:"png";}
    void external(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("No browser installed");}}
    interface Selection {void selected(String value);}
    void choose(String title,String[] options,String selected,Selection callback){new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options,Arrays.asList(options).indexOf(selected),(d,i)->{d.dismiss();callback.selected(options[i]);}).setNegativeButton("Cancel",null).show();}
    void section(String title){space(body,24);body.addView(sub(title.toUpperCase(Locale.ROOT)));space(body,10);}
    LinearLayout panel(){LinearLayout p=vertical();p.setPadding(dp(16),dp(4),dp(16),dp(4));p.setBackground(shape(surface,16));body.addView(p);return p;}
    void row(LinearLayout panel,String title,String subtitle,String value,Runnable action){LinearLayout row=horizontal();row.setPadding(0,dp(12),0,dp(12));row.setMinimumHeight(dp(60));LinearLayout copy=vertical();copy.addView(label(title,15,false));if(!subtitle.isEmpty()){space(copy,4);copy.addView(sub(subtitle));}grow(row,copy);TextView end=sub(value+"  ›");end.setPadding(dp(10),0,0,0);row.addView(end);row.setOnClickListener(v->action.run());if(panel.getChildCount()>0)divider(panel);panel.addView(row);}
    void divider(LinearLayout p){View lineView=new View(this);lineView.setBackgroundColor(line);p.addView(lineView,new LinearLayout.LayoutParams(-1,dp(1)));}
    void toggle(LinearLayout p,String title,String subtitle,String key,boolean fallback,Runnable changed){LinearLayout row=horizontal();row.setPadding(0,dp(12),0,dp(12));LinearLayout copy=vertical();copy.addView(label(title,15,false));if(!subtitle.isEmpty()){space(copy,4);copy.addView(sub(subtitle));}grow(row,copy);Switch s=new Switch(this);s.setContentDescription(title);s.setChecked(library.flag(key,fallback));s.setButtonTintList(null);s.setThumbTintList(android.content.res.ColorStateList.valueOf(0xfff0f3ed));s.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));row.addView(s);s.setOnCheckedChangeListener((b,on)->{library.putFlag(key,on);changed.run();});if(p.getChildCount()>0)divider(p);p.addView(row);}
    String versionLabel(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "1";}}
    void settings() {
        header("");body.addView(label("Settings",29,true));
        section("Appearance");LinearLayout appearance=panel();
        row(appearance,"Theme","",library.value("theme","Dark"),()->choose("Theme",new String[]{"Dark","Light"},library.value("theme","Dark"),s->{library.putValue("theme",s);render();}));
        row(appearance,"Accent color","",library.value("accent","#B2FF72").equals("#B2FF72")?"Lime":library.value("accent","").equals("#83D5FF")?"Sky":"Lilac",()->choose("Accent color",new String[]{"Lime","Sky","Lilac"},"",s->{library.putValue("accent",s.equals("Lime")?"#B2FF72":s.equals("Sky")?"#83D5FF":"#D4B3FF");render();}));
        row(appearance,"Grid columns","",library.value("columns","2"),()->choose("Grid columns",new String[]{"1","2","3"},library.value("columns","2"),s->{library.putValue("columns",s);render();}));
        section("Browsing");LinearLayout browsing=panel();
        toggle(browsing,"Safe only","Include General and Safe ratings","safe",true,()->{livePosts.clear();page=0;liveEnded=false;});
        toggle(browsing,"Autoplay videos","Start videos when opening artwork","autoplay",false,()->{});
        toggle(browsing,"Show artwork details","Show scores and creators in the gallery","details",true,()->{});
        row(browsing,"API credentials","Save & connect switches to online browsing",library.value("apiKey","").isEmpty()?"Not configured":"Configured",this::credentials);
        row(browsing,"Test connection","Check API access without changing your filters","Test",this::testConnection);
        section("Storage");LinearLayout storage=panel();row(storage,"Download quality","Original file or gallery preview",library.value("quality","Original"),()->choose("Download quality",new String[]{"Original","Preview"},library.value("quality","Original"),s->{library.putValue("quality",s);render();}));
        row(storage,"Image cache","Downloaded files and saves are kept",String.format(Locale.US,"%.1f MB · Clear",remote.cacheSize()/1048576.0),()->{remote.clear();toast("Image cache cleared");render();});
        section("Privacy");LinearLayout privacy=panel();toggle(privacy,"App lock","Require device screen lock on return","lock",false,()->{if(library.flag("lock",false)){unlocked=false;authenticate();}});
        row(privacy,"Clear search history","Remove recent searches from this device","Clear",()->new AlertDialog.Builder(this).setTitle("Clear search history?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{library.putValue("history","[]");toast("Search history cleared");}).show());
        section("About");LinearLayout about=panel();row(about,"M34 prototype","Android 17+ · ARM64 only · Native Android",versionLabel(),()->new AlertDialog.Builder(this).setTitle("M34 · "+versionLabel()).setMessage("A working artwork viewer prototype.\n\nM34 connects directly to the Rule34 API using your credentials. Saves, collections and likes are local to this device.\n\nNot affiliated with Rule34. No analytics or ads.").setPositiveButton("Done",null).show());space(body,24);TextView footer=sub("M34  ·  Made for discovery");footer.setGravity(Gravity.CENTER);body.addView(footer);
    }
    void testConnection() {
        ApiConfig config;
        try {config=ApiConfig.credentials(library.value("userId",""),library.apiKey(),"");}
        catch(Exception e){new AlertDialog.Builder(this).setTitle("API setup needed").setMessage("Enter your user ID and API key, or paste the complete API Access Credentials string.").setNegativeButton("Cancel",null).setPositiveButton("Edit credentials",(d,w)->credentials()).show();return;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connection test").setMessage("Connecting to Rule34 over HTTPS…").setPositiveButton("Done",null).create();dialog.show();
        remote.test(config.user,config.key,new Remote.Result<Remote.PostPage>(){
            public void success(Remote.PostPage response){if(isDestroyed()||!dialog.isShowing())return;dialog.setTitle(response.received>0?"Post data received":"Empty API response");dialog.setMessage(response.summary()+"\n\nOpen Explore to browse.");}
            public void failure(String message){if(isDestroyed()||!dialog.isShowing())return;dialog.setTitle("Connection failed");dialog.setMessage(message);}
        });
    }
    void credentials(){
        LinearLayout box=vertical();box.setPadding(dp(22),dp(12),dp(22),dp(12));
        EditText user=edit("Numeric user ID",library.value("userId",""));user.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText key=edit("API key or complete credentials string","");key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(user);space(box,12);box.addView(key);space(box,12);
        TextView help=sub("Paste the full &api_key=…&user_id=… string into the key field, or enter the two values separately. Leave the key blank to keep your saved key. Save & connect opens the live feed.");box.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect to Rule34").setView(box).setNegativeButton("Cancel",null)
            .setNeutralButton("Remove",(d,w)->{try{library.apiKey("");library.prefs.edit().putString("userId","").apply();livePosts.clear();page=0;liveEnded=false;toast("Credentials removed");render();}catch(Exception e){toast("Could not update credentials");}})
            .setPositiveButton("Save & connect",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            ApiConfig config;
            try{config=ApiConfig.credentials(user.getText().toString(),key.getText().toString(),key.getText().toString().isBlank()?library.apiKey():"");}
            catch(IllegalArgumentException e){help.setText(e.getMessage());return;}
            catch(Exception e){help.setText("Could not read the saved key. Enter it again in the key field.");return;}
            try{library.apiKey(config.key);}
            catch(Exception e){help.setText("Could not encrypt the API key. Credentials were not saved.");return;}
            library.prefs.edit().putString("userId",config.user).apply();
            livePosts.clear();page=0;liveEnded=false;query="";order="Latest";tab=0;detail=null;dialog.dismiss();toast("Credentials saved. Connecting…");render();
        }));dialog.show();
    }
    final class Icon extends View {
        final int kind,color;final Paint p=new Paint(3);Icon(int kind,int color){super(MainActivity.this);this.kind=kind;this.color=color;}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();if(kind==0){path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.moveTo(5,10);path.lineTo(5,21);path.lineTo(10,21);path.lineTo(10,15);path.lineTo(14,15);path.lineTo(14,21);path.lineTo(19,21);path.lineTo(19,10);c.drawPath(path,p);}else if(kind==1){c.drawCircle(10,10,7,p);c.drawLine(15,15,21,21,p);}else if(kind==2){path.moveTo(6,3);path.lineTo(18,3);path.lineTo(18,21);path.lineTo(12,17);path.lineTo(6,21);path.close();c.drawPath(path,p);}else{c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double angle=i*Math.PI/4;c.drawLine(12+(float)Math.cos(angle)*8,12+(float)Math.sin(angle)*8,12+(float)Math.cos(angle)*10,12+(float)Math.sin(angle)*10,p);}}c.restore();}
    }
}
