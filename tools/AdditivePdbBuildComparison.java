import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.PuzzleBoard;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.io.*;

/** Complete additive tables via an exact zero-cost quotient, never a point-query substitute. */
public final class AdditivePdbBuildComparison {
    private record Built(int[] distance, long expanded, int peak) {}
    private static int find(int[] parent,int x) {
        while(parent[x]!=x){parent[x]=parent[parent[x]];x=parent[x];}return x;
    }
    private static Built bfs(int[][] graph,int target) {
        int[] d=new int[graph.length];Arrays.fill(d,-1);
        ArrayDeque<Integer> q=new ArrayDeque<>();q.add(target);d[target]=0;
        long expanded=0;int peak=1;
        while(!q.isEmpty()){
            int u=q.remove();expanded++;
            for(int v:graph[u]) if(d[v]<0){d[v]=d[u]+1;q.add(v);}
            peak=Math.max(peak,q.size());
        }
        for(int x:d)if(x<0)throw new AssertionError("Incomplete quotient");
        return new Built(d,expanded,peak);
    }
    private static Built bibfs(int[][] graph,int source,int target) {
        if(source==target)return new Built(new int[]{0},0,1);
        int[][] d={new int[graph.length],new int[graph.length]};
        Arrays.fill(d[0],-1);Arrays.fill(d[1],-1);
        List<ArrayDeque<Integer>> q=List.of(new ArrayDeque<>(),new ArrayDeque<>());
        d[0][source]=0;d[1][target]=0;q.get(0).add(source);q.get(1).add(target);
        long expanded=0;int peak=2;
        while(!q.get(0).isEmpty()&&!q.get(1).isEmpty()){
            int side=q.get(0).size()<=q.get(1).size()?0:1;
            int layer=q.get(side).size(),best=Integer.MAX_VALUE;
            while(layer-->0){
                int u=q.get(side).remove();expanded++;
                if(d[1-side][u]>=0)best=Math.min(best,d[side][u]+d[1-side][u]);
                for(int v:graph[u]){
                    if(d[side][v]<0){d[side][v]=d[side][u]+1;q.get(side).add(v);}
                    if(d[1-side][v]>=0)best=Math.min(best,d[side][v]+d[1-side][v]);
                }
                peak=Math.max(peak,q.get(0).size()+q.get(1).size());
            }
            if(best<Integer.MAX_VALUE)return new Built(new int[]{best},expanded,peak);
        }
        throw new AssertionError("Disconnected quotient");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Unique output directory required");
        Path out=Path.of(args[0]);Files.createDirectory(out);
        String run=Instant.now().toString().replace(':','-');
        int[][] goals={{1,2,3,4,5,6,7,8,0},{0,2,3,1,4,6,7,5,8}};
        try(PrintWriter csv=new PrintWriter(Files.newBufferedWriter(out.resolve("complete-build.csv"),StandardCharsets.UTF_8))){
            csv.println("run_id,goal_id,pattern,method,workload,repetition,states,components,preprocess_ms,reference_prepare_ms,build_ms,export_ms,expanded,peak_frontier_entries,validated_ranks,status,distance_file");
            for(int gid=0;gid<goals.length;gid++){
                PuzzleBoard goal=new PuzzleBoard(goals[gid],3);
                long start=System.nanoTime();
                PatternTable ref=new PatternTable(goal,new int[]{1,2,3},out.resolve("reference-"+gid+".pdb"));
                double reference=(System.nanoTime()-start)/1e6;
                start=System.nanoTime();int n=ref.states();int[] parent=new int[n];int[][] edges=new int[n][];
                for(int i=0;i<n;i++){parent[i]=i;edges[i]=ref.neighbors(i);}
                for(int u=0;u<n;u++)for(int edge:edges[u])if((edge&1)==0)parent[find(parent,u)]=find(parent,edge>>>1);
                Map<Integer,Integer> id=new LinkedHashMap<>();int[] component=new int[n];
                for(int i=0;i<n;i++)component[i]=id.computeIfAbsent(find(parent,i),unused->id.size());
                List<Set<Integer>> adjacency=new ArrayList<>();for(int i=0;i<id.size();i++)adjacency.add(new TreeSet<>());
                for(int u=0;u<n;u++)for(int edge:edges[u]){
                    int v=edge>>>1,a=component[u],b=component[v];
                    if(a!=b){if((edge&1)!=1)throw new AssertionError("Zero edge crossed components");adjacency.get(a).add(b);}
                }
                int[][] graph=new int[id.size()][];
                for(int i=0;i<graph.length;i++)graph[i]=adjacency.get(i).stream().mapToInt(Integer::intValue).toArray();
                for(int u=0;u<graph.length;u++)for(int v:graph[u])if(Arrays.binarySearch(graph[v],u)<0)throw new AssertionError("Asymmetric graph");
                int target=component[ref.rank(new int[]{goal.positionOf(0),goal.positionOf(1),goal.positionOf(2),goal.positionOf(3)})];
                double preprocess=(System.nanoTime()-start)/1e6;
                for(int rep=1;rep<=3;rep++)for(String method:rep%2==0?List.of("BiBFS","BFS"):List.of("BFS","BiBFS")){
                    start=System.nanoTime();Built built;
                    if(method.equals("BFS"))built=bfs(graph,target);
                    else{
                        int[] d=new int[graph.length];long expanded=0;int peak=0;
                        for(int source=0;source<graph.length;source++){Built one=bibfs(graph,source,target);d[source]=one.distance[0];expanded+=one.expanded;peak=Math.max(peak,one.peak);}
                        built=new Built(d,expanded,peak);
                    }
                    int[] values=new int[n];for(int rank=0;rank<n;rank++)values[rank]=built.distance[component[rank]];
                    double ms=(System.nanoTime()-start)/1e6;
                    for(int rank=0;rank<n;rank++)if(values[rank]!=ref.valueAt(rank))throw new AssertionError("Rank distance mismatch "+rank);
                    String file="goal-"+gid+"-"+method+"-"+rep+"-distances.txt";
                    start=System.nanoTime();StringBuilder text=new StringBuilder();for(int value:values)text.append(value).append('\n');
                    Files.writeString(out.resolve(file),text,StandardCharsets.UTF_8);double export=(System.nanoTime()-start)/1e6;
                    csv.printf(Locale.ROOT,"%s,%d,1-2-3,%s,complete-additive-quotient-table,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,SOLVED,%s%n",run,gid,method,rep,n,graph.length,preprocess,reference,ms,export,built.expanded,built.peak,n,file);csv.flush();
                    System.out.printf(Locale.ROOT,"goal=%d %s rep=%d ranks=%d components=%d build_ms=%.3f validated=ALL%n",gid,method,rep,n,graph.length,ms);
                }
            }
        }
    }
}
