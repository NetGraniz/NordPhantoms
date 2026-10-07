package com.nordfjell.nordphantoms;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable regional snapshots, indexed spatially; never queries a foreign entity. */
final class PhantomPositions {
    record Position(UUID world,double x,double y,double z) {}
    private record Cell(UUID world,int x,int z) {}
    private final Map<UUID,Position> byId=new ConcurrentHashMap<>();
    private final Map<Cell,Map<UUID,Position>> cells=new ConcurrentHashMap<>();
    private static int coordinate(double value){return (int)Math.floor(value/128.0);}
    private static Cell cell(Position p){return new Cell(p.world(),coordinate(p.x()),coordinate(p.z()));}
    synchronized void update(UUID id,Position next){
        Position previous=byId.put(id,next);
        if(previous!=null&&!cell(previous).equals(cell(next)))removeCell(id,previous);
        cells.computeIfAbsent(cell(next),key -> new ConcurrentHashMap<>()).put(id,next);
    }
    synchronized void remove(UUID id){Position previous=byId.remove(id);if(previous!=null)removeCell(id,previous);}
    private void removeCell(UUID id,Position previous){
        Cell key=cell(previous);Map<UUID,Position> entries=cells.get(key);
        if(entries!=null){entries.remove(id);if(entries.isEmpty())cells.remove(key);}
    }
    int count(UUID world,double x,double y,double z,double radius){
        int count=0;
        for(int cx=coordinate(x-radius);cx<=coordinate(x+radius);cx++)
            for(int cz=coordinate(z-radius);cz<=coordinate(z+radius);cz++){
                Map<UUID,Position> entries=cells.get(new Cell(world,cx,cz));if(entries==null)continue;
                for(Position p:entries.values())if(Math.abs(p.x()-x)<=radius&&Math.abs(p.y()-y)<=radius&&Math.abs(p.z()-z)<=radius)count++;
            }
        return count;
    }
    synchronized void clear(){byId.clear();cells.clear();}
}
