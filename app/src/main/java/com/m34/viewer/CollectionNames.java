package com.m34.viewer;

import java.util.Set;

final class CollectionNames {
    static String error(String name,String original,Set<String> names){
        if(name.isBlank())return "Enter a collection name";
        if(name.length()>40)return "Use at most 40 characters";
        if(name.equalsIgnoreCase("All saved"))return "This name is reserved";
        for(String existing:names)if(!existing.equals(original)&&existing.equalsIgnoreCase(name))return "A collection with this name already exists";
        return null;
    }
}
