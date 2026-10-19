import com.m34.viewer.Catalog;
import com.m34.viewer.Catalog.Art;
import java.util.*;

public final class CatalogTest {
    public static List<Art> demo() {
        String[] titles={"Neon horizon","Cloud citadel","Crimson grove","Silent sentinel","The long way home","Orbital dawn","Emerald falls","Beyond the gate","Golden hour","Midnight voyage","Alpine stillness","City of tomorrow","Moonlit passage","Paper mountains","Last light","Electric rain","Winter sanctuary","Ocean of stars"};
        String[] tags={"landscape fantasy sunset mountains planet","castle fantasy clouds sky architecture","forest red autumn nature shrine","robot forest sci_fi nature","landscape mountains adventure nature","planet space sky sci_fi","waterfall landscape forest nature","portal fantasy landscape ruins","sunset landscape desert mountains","space planet night sci_fi","mountains lake nature landscape","cityscape sci_fi neon night","night moon forest landscape","mountains abstract pastel landscape","sunset ocean clouds landscape","cityscape neon rain sci_fi","winter snow castle landscape","space ocean night stars"};
        List<Art> result=new ArrayList<>();
        for(int i=0;i<titles.length;i++) result.add(new Art("demo-"+i,titles[i],new String[]{"M34 Studio","Northline","ArdenKai"}[i%3],tags[i],i,4800-i*193,"","","s",1600,2000));
        return result;
    }

    static int checks;
    static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) {
        var demo=demo();
        check(demo.size()==18,"fixture gallery is populated");
        check(demo.stream().map(a->a.id).distinct().count()==18,"stable unique save IDs");
        check(Catalog.filter(demo,"landscape -night",true,"Latest").stream().allMatch(a->a.tags.contains("landscape")&&!a.tags.contains("night")),"tag AND and exclusions");
        check(Catalog.filter(demo,"LANDSCAPE",true,"Latest").size()>0,"case insensitive search");
        check(Catalog.filter(demo,"unfindabletag",true,"Latest").isEmpty(),"empty state");
        check(Catalog.safeQuery("landscape rating:explicit -rating:safe",true).equals("landscape -rating:questionable -rating:explicit"),"safe filter cannot be overridden by query");
        check(Catalog.safeQuery("RATING:E fantasy",true).equals("fantasy -rating:questionable -rating:explicit"),"safe filter handles case");
        check(Catalog.safeQuery("rating:explicit",false).equals("rating:explicit"),"all ratings preserves query");
        var explicit=new Catalog.Art("test","Test","Test","landscape",0,99,"","","e",100,100);
        var unknown=new Catalog.Art("unknown","Unknown","Test","landscape",0,99,"","","unknown",100,100);
        var mixed=new ArrayList<>(demo);mixed.add(explicit);mixed.add(unknown);
        check(Catalog.filter(mixed,"",true,"Title").size()==18,"safe gallery excludes explicit and unknown ratings");
        check(Catalog.filter(mixed,"",false,"Title").size()==20,"all ratings can be selected");
        var top=Catalog.filter(demo,"",true,"Top rated");
        for(int i=1;i<top.size();i++)check(top.get(i-1).score>=top.get(i).score,"score order");
        check(demo.get(0).id.equals("demo-0"),"filter sorting does not mutate input");
        System.out.println("Passed "+checks+" catalog checks");
    }
}
