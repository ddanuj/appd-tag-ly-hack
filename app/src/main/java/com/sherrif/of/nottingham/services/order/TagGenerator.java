package com.sherrif.of.nottingham.services.order;

import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;

import java.util.List;

public class TagGenerator {


    public static String text = "Bought six thousand units of GME at 45 USD. Let's squeeze the shorters";

//    public static void main(String[] args) {
//        // Create a document. No computation is done yet.
//        Document doc = new Document(text);
//        for (Sentence sent : doc.sentences()) {  // Will iterate over two sentences
//            // When we ask for the lemma, it will load and run the part of speech tagger
//            System.out.println("The third lemma of the sentence '" + sent + "' is " + sent.lemma(2));
//            // When we ask for the parse, it will load and run the parser
//            System.out.println("The parse of the sentence '" + sent + "' is " + sent.parse());
//            // ....nerTags()
//            List<String> nameEntities = sent.nerTags();
//            System.out.println("The Named Entity Tags of the sentence '" + sent + "' is" + nameEntities);
//            List<String> tags = sent.mentions("ORGANIZATION");
//            System.out.println("Tags generated are " + tags);
//
//        }
//    }

    public List<String> generateTagsFromUnstructuredInput(String text, String identifier) {
        // Create a document. No computation is done yet.
        Document doc = new Document(text);
        for (Sentence sent : doc.sentences()) {  // Will iterate over two sentences
            // When we ask for the lemma, it will load and run the part of speech tagger
            System.out.println("The third lemma of the sentence '" + sent + "' is " + sent.lemma(2));
            // When we ask for the parse, it will load and run the parser
            System.out.println("The parse of the sentence '" + sent + "' is " + sent.parse());
            // ....nerTags()
            List<String> nameEntities = sent.nerTags();
            System.out.println("The Named Entity Tags of the sentence '" + sent + "' is " + nameEntities);
            List<String> tags = sent.mentions(identifier);
            System.out.println("Tags generated are-" + tags);
            if (!tags.isEmpty()) {
                return tags;
            }
        }
        return null;
    }
}
