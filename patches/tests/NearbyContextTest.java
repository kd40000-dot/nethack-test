package com.tbd.NetHack5.tutorial;

/** Regression for the contextual UI's two-tile maximum (diagonals count as one). */
public final class NearbyContextTest {
    private static int checked;
    private static void ok(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
        checked++;
    }
    private static TutorialEngine.State state() {
        TutorialEngine.State s=new TutorialEngine.State();
        s.gameReady=true;s.hp=16;s.maxHp=16;
        return s;
    }
    public static void main(String[] args) {
        ok(TutorialEngine.inLocalContext(0,0),"own tile is local");
        ok(TutorialEngine.inLocalContext(1,1),"diagonal adjacent is local");
        ok(TutorialEngine.inLocalContext(-2,2),"two-tile diagonal is included");
        ok(TutorialEngine.inLocalContext(2,0),"two tiles cardinal included");
        ok(!TutorialEngine.inLocalContext(3,0),"three tiles cardinal excluded");
        ok(!TutorialEngine.inLocalContext(-3,-2),"three tiles diagonal excluded");
        ok(!TutorialEngine.inLocalContext(0,6),"six tiles excluded");
        TutorialEngine.State s=state();
        s.visibleStairs.add(new TutorialEngine.Stair(6,0,true));
        ok(TutorialEngine.assess(s).title.equals("Explore carefully"),"remote stair cannot enter primary advice");
        s.visibleStairs.add(new TutorialEngine.Stair(2,2,false));
        ok(TutorialEngine.assess(s).title.contains("Staircase up"),"two-tile stair appears");
        s.visibleStairs.clear();
        s.visibleStairs.add(new TutorialEngine.Stair(2,1,true));
        s.visibleStairs.add(new TutorialEngine.Stair(-1,0,false));
        ok(TutorialEngine.assess(s).title.contains("Staircase up"),"nearest stair chosen irrespective of list order");
        s.visibleStairs.clear();
        s.visibleStairs.add(new TutorialEngine.Stair(0,0,true));
        ok(TutorialEngine.assess(s).title.equals("Explore carefully"),"underfoot stair handled by separate state flag");
        System.out.println("PASS: "+checked+" two-tile context assertions");
    }
}
