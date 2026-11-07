package com.franchiseguide.desktop;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Concurrent callers, controlled upstream, no provider credentials or network. */
public final class SteamConcurrencyTest {
    private static int checks;
    private static final String KEY = "b".repeat(32);
    private static Map<String,String> query(String user,String game,String language) {
        return Map.of("steamid",user,"appid",game,"language",language);
    }
    private static Map<String,String> query() { return query("76561198000000001","570","english"); }
    private static void check(String name,boolean condition) {
        if(!condition)throw new AssertionError(name);
        checks++;System.out.println("PASS "+name);
    }
    private static void pending(Future<?> future) throws Exception {
        try { future.get(80,TimeUnit.MILLISECONDS); throw new AssertionError("Expected shared pending read"); }
        catch(TimeoutException expected) { /* upstream is deliberately blocked */ }
    }
    private static Throwable failure(Future<?> future) throws Exception {
        try { future.get(4,TimeUnit.SECONDS);throw new AssertionError("Expected read failure"); }
        catch(ExecutionException error) { return error.getCause(); }
    }
    private static void await(CountDownLatch latch) throws Exception {
        if(!latch.await(4,TimeUnit.SECONDS))throw new AssertionError("Timed out waiting for test gate");
    }
    private static String schema() {
        return "{\"game\":{\"gameName\":\"Fixture\",\"availableGameStats\":{\"achievements\":[{\"name\":\"one\"}]}}}";
    }
    private static String payload(java.net.URI uri) {
        return uri.getPath().contains("GetSchemaForGame") ? schema() : "{\"fixture\":true}";
    }
    public static void main(String[] args) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(16);
        try {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            AtomicInteger calls=new AtomicInteger();
            SteamBridge bridge=new SteamBridge(KEY,uri->{
                calls.incrementAndGet();if(uri.getPath().contains("GetSchemaForGame")){entered.countDown();await(release);}return payload(uri);
            });
            List<Future<String>> readers=new ArrayList<>();
            try {
                readers.add(pool.submit(()->bridge.lookup(query())));await(entered);
                for(int i=0;i<12;i++)readers.add(pool.submit(()->bridge.lookup(query())));
                for(Future<String> reader:readers)pending(reader);
                check("13 equal reads share one pending schema request",calls.get()==1);
            } finally { release.countDown(); }
            Set<String> results=new HashSet<>();for(Future<String> reader:readers)results.add(reader.get(4,TimeUnit.SECONDS));
            check("All shared callers receive the exact same bundle",results.size()==1&&calls.get()==3);
            bridge.lookup(query());check("Completed shared bundle remains cached",calls.get()==3);
            bridge.lookup(query("76561198000000002","570","english"));
            check("A different Steam account is not conflated",calls.get()==6);
            bridge.lookup(query("76561198000000001","570","french"));
            check("A different language is not conflated",calls.get()==9);
            bridge.lookup(query("76561198000000001","571","english"));
            check("A different game is not conflated",calls.get()==12);
            // The 12 followers must not consume 12 rate-limit tokens.
            for(int i=0;i<8;i++)bridge.lookup(query("76561198000000001",String.valueOf(600+i),"english"));
            boolean rate=false;try{bridge.lookup(query("76561198000000001","700","english"));}catch(SteamBridge.Failure e){rate=e.status==429;}
            check("Only unique owner reads consume rate tokens",rate&&calls.get()==36);

            CountDownLatch failureEntered=new CountDownLatch(1),failureRelease=new CountDownLatch(1);
            AtomicInteger failedCalls=new AtomicInteger();
            SteamBridge failing=new SteamBridge(KEY,uri->{
                int call=failedCalls.incrementAndGet();
                if(call==1){failureEntered.countDown();await(failureRelease);throw new IOException("controlled failure");}
                return payload(uri);
            });
            Future<String> owner=pool.submit(()->failing.lookup(query()));await(failureEntered);
            Future<String> follower=pool.submit(()->failing.lookup(query()));
            try {pending(follower);}finally{failureRelease.countDown();}
            check("Upstream failure propagates to every shared caller",failure(owner) instanceof IOException&&failure(follower) instanceof IOException&&failedCalls.get()==1);
            check("Failure is evicted and a healthy retry is possible",failing.lookup(query()).contains("\"provider\":\"steam\"")&&failedCalls.get()==4);

            CountDownLatch slotsEntered=new CountDownLatch(2),slotsRelease=new CountDownLatch(1);
            SteamBridge limited=new SteamBridge(KEY,uri->{
                if(uri.getPath().contains("GetSchemaForGame")){slotsEntered.countDown();await(slotsRelease);}return payload(uri);
            });
            Future<String> first=pool.submit(()->limited.lookup(query()));
            Future<String> second=pool.submit(()->limited.lookup(query("76561198000000002","570","english")));
            try {
                await(slotsEntered);boolean saturated=false;
                try{limited.lookup(query("76561198000000003","570","english"));}catch(SteamBridge.Failure e){saturated=e.status==429;}
                check("Only two distinct bundles can be in flight",saturated);
                Future<String> cancelled=pool.submit(()->limited.lookup(query()));pending(cancelled);cancelled.cancel(true);
                check("A waiting caller can be interrupted independently",cancelled.isCancelled());
            }finally{slotsRelease.countDown();}
            check("Cancelling a follower does not cancel either owner",first.get(4,TimeUnit.SECONDS)!=null&&second.get(4,TimeUnit.SECONDS)!=null);
            check("Owner completion releases capacity",limited.lookup(query("76561198000000003","570","english"))!=null);

            AtomicInteger emptyCalls=new AtomicInteger();
            SteamBridge empty=new SteamBridge(KEY,uri->{emptyCalls.incrementAndGet();return "{\"game\":{\"gameName\":\"Fixture\",\"availableGameStats\":{\"achievements\":[]}}}";});
            check("No-achievement game reads only its schema",empty.lookup(query()).contains("\"noAchievements\":true")&&emptyCalls.get()==1);
            AtomicInteger nestedCalls=new AtomicInteger();
            SteamBridge nested=new SteamBridge(KEY,uri->{nestedCalls.incrementAndGet();return uri.getPath().contains("GetSchemaForGame")?"{\"metadata\":{\"achievements\":[]},\"game\":{\"gameName\":\"Fixture\",\"availableGameStats\":{\"achievements\":[{\"name\":\"one\"}]}}}":"{}";});
            check("Unrelated empty metadata cannot erase game achievements",nested.lookup(query()).contains("\"noAchievements\":false")&&nestedCalls.get()==3);
            SteamBridge malformed=new SteamBridge(KEY,uri->"{");
            boolean rejected=false;try{malformed.lookup(query());}catch(Exception expected){rejected=true;}
            check("Malformed schema fails before player and rarity reads",rejected);
        } finally { pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS); }
        System.out.println("Steam concurrency: "+checks+" assertions passed.");
    }
}
