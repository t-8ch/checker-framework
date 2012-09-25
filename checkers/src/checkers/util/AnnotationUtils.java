package checkers.util;

/*>>>
import checkers.nullness.quals.*;
*/
import checkers.source.SourceChecker;
import checkers.types.QualifierHierarchy;

import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * A utility class for working with annotations.
 */
public class AnnotationUtils {

    // Class cannot be instantiated.
    private AnnotationUtils() { throw new AssertionError("Class AnnotationUtils cannot be instantiated."); }

    // **********************************************************************
    // Factory Methods to create instances of AnnotationMirror
    // **********************************************************************

    /** Caching for annotation creation. */
    private static final Map<CharSequence, AnnotationMirror> annotationsFromNames
        = new HashMap<CharSequence, AnnotationMirror>();

    /**
     * Creates an {@link AnnotationMirror} given by a particular
     * fully-qualified name.  getElementValues on the result returns an
     * empty map.
     *
     * @param elements the element utilities to use
     * @param name the name of the annotation to create
     * @return an {@link AnnotationMirror} of type {@code} name
     */
    public static AnnotationMirror fromName(Elements elements, CharSequence name) {
        if (annotationsFromNames.containsKey(name))
            return annotationsFromNames.get(name);
        final DeclaredType annoType = typeFromName(elements, name);
        if (annoType == null)
            return null;
        if (annoType.asElement().getKind() != ElementKind.ANNOTATION_TYPE) {
            SourceChecker.errorAbort(annoType + " is not an annotation");
            return null; // dead code
        }
        AnnotationMirror result = new AnnotationMirror() {
            String toString = "@" + annoType;

            @Override
            public DeclaredType getAnnotationType() {
                return annoType;
            }
            @Override
            public Map<? extends ExecutableElement, ? extends AnnotationValue>
                getElementValues() {
                return Collections.emptyMap();
            }
            @Override
            public String toString() {
                return toString;
            }
        };
        annotationsFromNames.put(name, result);
        return result;
    }

    /**
     * Creates an {@link AnnotationMirror} given by a particular annotation
     * class.
     *
     * @param elements the element utilities to use
     * @param clazz the annotation class
     * @return an {@link AnnotationMirror} of type given type
     */
    public static AnnotationMirror fromClass(Elements elements, Class<? extends Annotation> clazz) {
        return fromName(elements, clazz.getCanonicalName());
    }

    /**
     * A utility method that converts a {@link CharSequence} (usually a {@link
     * String}) into a {@link TypeMirror} named thereby.
     *
     * @param elements the element utilities to use
     * @param name the name of a type
     * @return the {@link TypeMirror} corresponding to that name
     */
    private static DeclaredType typeFromName(Elements elements, CharSequence name) {
        /*@Nullable*/ TypeElement typeElt = elements.getTypeElement(name);
        if (typeElt == null)
            return null;

        return (DeclaredType) typeElt.asType();
    }


    // **********************************************************************
    // Helper methods to handle annotations.  mainly workaround
    // AnnotationMirror.equals undesired property
    // (I think the undesired property is that it's reference equality.)
    // **********************************************************************

    /**
     * @return the fully-qualified name of an annotation as a Name
     */
    public static final Name annotationName(AnnotationMirror annotation) {
        final DeclaredType annoType = annotation.getAnnotationType();
        final TypeElement elm = (TypeElement) annoType.asElement();
        return elm.getQualifiedName();
    }

    /**
     * Checks if both annotations are the same.
     *
     * Returns true iff both annotations are of the same type and have the
     * same annotation values.  This behavior defers from
     * {@code AnnotationMirror.equals(Object)}.  The equals method returns
     * true iff both annotations are the same and annotate the same annotation
     * target (e.g. field, variable, etc).
     *
     * @return true iff a1 and a2 are the same annotation
     */
    public static boolean areSame(/*@Nullable*/ AnnotationMirror a1, /*@Nullable*/ AnnotationMirror a2) {
        if (a1 != null && a2 != null) {
            if (!annotationName(a1).equals(annotationName(a2))) {
                return false;
            }

            Map<? extends ExecutableElement, ? extends AnnotationValue> elval1 = getElementValuesWithDefaults(a1);
            Map<? extends ExecutableElement, ? extends AnnotationValue> elval2 = getElementValuesWithDefaults(a2);

            return elval1.toString().equals(elval2.toString());
        }

        // only true, iff both are null
        return a1 == a2;
    }

    /**
     * @see #areSame(AnnotationMirror, AnnotationMirror)
     * @return true iff a1 and a2 have the same annotation type
     */
    public static boolean areSameIgnoringValues(AnnotationMirror a1, AnnotationMirror a2) {
        if (a1 != null && a2 != null)
            return annotationName(a1).equals(annotationName(a2));
        return a1 == a2;
    }

    /**
     * Checks that the annotation {@code am} has the name {@code aname}. Values
     * are ignored.
     */
    public static boolean areSameByName(AnnotationMirror am, String aname) {
        Name amname = AnnotationUtils.annotationName(am);
        return amname.toString().equals(aname);
    }

    /**
     * Checks that the annotation {@code am} has the name of {@code anno}.
     * Values are ignored.
     */
    public static boolean areSameByClass(AnnotationMirror am,
            Class<? extends Annotation> anno) {
        return areSameByName(am, anno.getCanonicalName());
    }

    /**
     * Checks that two collections contain the same annotations.
     *
     * @return true iff c1 and c2 contain the same annotations
     */
    public static boolean areSame(Collection<? extends AnnotationMirror> c1, Collection<? extends AnnotationMirror> c2) {
        if (c1.size() != c2.size())
            return false;
        if (c1.size() == 1)
            return areSame(c1.iterator().next(), c2.iterator().next());

        Set<AnnotationMirror> s1 = createAnnotationSet();
        Set<AnnotationMirror> s2 = createAnnotationSet();
        s1.addAll(c1);
        s2.addAll(c2);

        // depend on the fact that Set is an ordered set.
        Iterator<AnnotationMirror> iter1 = s1.iterator();
        Iterator<AnnotationMirror> iter2 = s2.iterator();

        while (iter1.hasNext()) {
            AnnotationMirror anno1 = iter1.next();
            AnnotationMirror anno2 = iter2.next();
            if (!areSame(anno1, anno2))
                return false;
        }
        return true;
    }

    /**
     * Checks that the collection contains the annotation.
     * Using Collection.contains does not always work, because it
     * does not use areSame for comparison.
     *
     * @return true iff c contains anno, according to areSame.
     */
    public static boolean containsSame(Collection<? extends AnnotationMirror> c, AnnotationMirror anno) {
        for(AnnotationMirror an : c) {
            if(AnnotationUtils.areSame(an, anno)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the annotation from hierarchy identified by its 'top' annotation
     * from a set of annotations.
     *
     * @param qualHierarchy
     *            The {@link QualifierHierarchy} for subtyping tests.
     * @param annos
     *            The set of annotations.
     * @param top
     *            The top annotation of the hierarchy to consider.
     */
    public static AnnotationMirror getAnnotationInHierarchy(
            QualifierHierarchy qualHierarchy,
            Collection<AnnotationMirror> annos, AnnotationMirror top) {
        AnnotationMirror annoInHierarchy = null;
        for (AnnotationMirror rhsAnno : annos) {
            if (qualHierarchy.isSubtype(rhsAnno, top)) {
                annoInHierarchy = rhsAnno;
            }
        }
        return annoInHierarchy;
    }

    /**
     * Checks that the collection contains the annotation ignoring values.
     * Using Collection.contains does not always work, because it
     * does not use areSameIgnoringValues for comparison.
     *
     * @return true iff c contains anno, according to areSameIgnoringValues.
     */
    public static boolean containsSameIgnoringValues(Collection<? extends AnnotationMirror> c, AnnotationMirror anno) {
        for(AnnotationMirror an : c) {
            if(AnnotationUtils.areSameIgnoringValues(an, anno)) {
                return true;
            }
        }
        return false;
    }

    private static final Comparator<AnnotationMirror> ANNOTATION_ORDERING
    = new Comparator<AnnotationMirror>() {
        @Override
        public int compare(AnnotationMirror a1, AnnotationMirror a2) {
            String n1 = a1.toString();
            String n2 = a2.toString();

            return n1.compareTo(n2);
        }
    };

    /**
     * provide ordering for {@link AnnotationMirror} based on their fully
     * qualified name.  The ordering ignores annotation values when ordering.
     *
     * The ordering is meant to be used as {@link TreeSet} or {@link TreeMap}
     * ordering.  A {@link Set} should not contain two annotations that only
     * differ in values.
     */
    public static Comparator<AnnotationMirror> annotationOrdering() {
        return ANNOTATION_ORDERING;
    }

    /**
     * Create a map suitable for storing {@link AnnotationMirror} as keys.
     *
     * It can store one instance of {@link AnnotationMirror} of a given
     * declared type, regardless of the annotation element values.
     *
     * @param <V> the value of the map
     * @return a new map with {@link AnnotationMirror} as key
     */
    public static <V> Map<AnnotationMirror, V> createAnnotationMap() {
        return new TreeMap<AnnotationMirror, V>(annotationOrdering());
    }

    /**
     * Constructs a {@link Set} suitable for storing {@link AnnotationMirror}s.
     *
     * It stores at most once instance of {@link AnnotationMirror} of a given
     * type, regardless of the annotation element values.
     *
     * @return a new set to store {@link AnnotationMirror} as element
     */
    public static Set<AnnotationMirror> createAnnotationSet() {
        return new TreeSet<AnnotationMirror>(annotationOrdering());
    }

    /** Returns true if the given annotation has a @Inherited meta-annotation. */
    public static boolean hasInheritedMeta(AnnotationMirror anno) {
        return anno.getAnnotationType().asElement().getAnnotation(Inherited.class) != null;
    }


    // **********************************************************************
    // Extractors for annotation values
    // **********************************************************************

    /**
     * Returns the values of an annotation's attributes, including defaults.
     * The method with the same name in JavacElements cannot be used directly,
     * because it includes a cast to Attribute.Compound, which doesn't hold
     * for annotations generated by the Checker Framework.
     *
     * @see AnnotationMirror#getElementValues()
     * @see JavacElements#getElementValuesWithDefaults(AnnotationMirror)
     *
     * @param ad  annotation to examine
     * @return the values of the annotation's elements, including defaults
     */
    public static Map<? extends ExecutableElement, ? extends AnnotationValue>
    getElementValuesWithDefaults(AnnotationMirror ad) {
        Map<ExecutableElement, AnnotationValue> valMap
            = new HashMap<ExecutableElement, AnnotationValue>();
        if (ad.getElementValues() != null) {
            valMap.putAll(ad.getElementValues());
        }
        for (ExecutableElement meth :
            ElementFilter.methodsIn(ad.getAnnotationType().asElement().getEnclosedElements())) {
            AnnotationValue defaultValue = meth.getDefaultValue();
            if (defaultValue != null && !valMap.containsKey(meth))
                valMap.put(meth, defaultValue);
        }
        return valMap;
    }

    /**
     * Get the attribute with the name {@code name} of the annotation
     * {@code anno}. The result is expected to have type {@code expectedType}.
     *
     * <p>
     * <em>Note 1</em>: The method does not work well for attributes of an array
     * type (as it would return a list of {@link AnnotationValue}s). Use
     * {@code getElementValueArray} instead.
     *
     * <p>
     * <em>Note 2</em>: The method does not work for attributes of an enum type,
     * as the AnnotationValue is a VarSymbol and would be cast to the enum type,
     * which doesn't work. Use {@code getElementValueEnum} instead.
     *
     *
     * @param anno the annotation to disassemble
     * @param name the name of the attribute to access
     * @param expectedType the expected type used to cast the return type
     * @param useDefaults whether to apply default values to the attribute.
     * @return the value of the attribute with the given name
     */
    public static <T> T getElementValue(AnnotationMirror anno, CharSequence name,
            Class<T> expectedType, boolean useDefaults) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> valmap;
        if (useDefaults) {
            valmap = getElementValuesWithDefaults(anno);
        } else {
            valmap = anno.getElementValues();
        }
        for (ExecutableElement elem : valmap.keySet()) {
            if (elem.getSimpleName().contentEquals(name)) {
                AnnotationValue val = valmap.get(elem);
                return expectedType.cast(val.getValue());
            }
        }
        SourceChecker.errorAbort("No element with name \'" + name + "\' in annotation " + anno);
        return null; // dead code
    }

    /**
     * Version that is suitable for Enum elements.
     */
    public static <T extends Enum<T>> T getElementValueEnum(
            AnnotationMirror anno, CharSequence name, Class<T> t,
            boolean useDefaults) {
        VarSymbol vs = getElementValue(anno, name, VarSymbol.class, useDefaults);
        T value = Enum.valueOf(t, vs.getSimpleName().toString());
        return value;
    }

    /**
     * Get the attribute with the name {@code name} of the annotation
     * {@code anno}, where the attribute has an array type. One element of the
     * result is expected to have type {@code expectedType}.
     *
     * Parameter useDefaults is used to determine whether default values
     * should be used for annotation values. Finding defaults requires
     * more computation, so should be false when no defaulting is needed.
     *
     * @param anno the annotation to disassemble
     * @param name the name of the attribute to access
     * @param expectedType the expected type used to cast the return type
     * @param useDefaults whether to apply default values to the attribute.
     * @return the value of the attribute with the given name
     */
    public static <T> List<T> getElementValueArray(AnnotationMirror anno,
            CharSequence name, Class<T> expectedType, boolean useDefaults) {
        @SuppressWarnings("unchecked")
        List<AnnotationValue> la = getElementValue(anno, name, List.class, useDefaults);
        List<T> result = new ArrayList<T>(la.size());
        for (AnnotationValue a : la) {
            result.add(expectedType.cast(a.getValue()));
        }
        return result;
    }

    /**
     * Get the attribute with the name {@code name} of the annotation
     * {@code anno}, or the default value if no attribute is present explicitly,
     * where the attribute has an array type and the elements are {@code Enum}s.
     * One element of the result is expected to have type {@code expectedType}.
     */
    public static <T extends Enum<T>> List<T> getElementValueEnumArray(
            AnnotationMirror anno, CharSequence name, Class<T> t,
            boolean useDefaults) {
        @SuppressWarnings("unchecked")
        List<AnnotationValue> la = getElementValue(anno, name, List.class, useDefaults);
        List<T> result = new ArrayList<T>(la.size());
        for (AnnotationValue a : la) {
            T value = Enum.valueOf(t, a.getValue().toString());
            result.add(value);
        }
        return result;
    }

    /**
     * Get the Name of the class that is referenced by attribute 'name'.
     * This is a convenience method for the most common use-case.
     * Like getElementValue(anno, name, ClassType.class).getQualifiedName(), but
     * this method ensures consistent use of the qualified name.
     */
    public static Name getElementValueClassName(AnnotationMirror anno, CharSequence name,
            boolean useDefaults) {
        Type.ClassType ct = getElementValue(anno, name, Type.ClassType.class, useDefaults);
        // TODO:  Is it a problem that this returns the type parameters too?  Should I cut them off?
        return ct.asElement().getQualifiedName();
    }

    /**
     * Get the Class that is referenced by attribute 'name'.
     * This method uses Class.forName to tr to load the class. It returns
     * null if the class wasn't found.
     */
    public static Class<?> getElementValueClass(AnnotationMirror anno, CharSequence name,
            boolean useDefaults) {
        Name cn = getElementValueClassName(anno, name, useDefaults);
        try {
            Class<?> cls =  Class.forName(cn.toString());
            return cls;
        } catch (ClassNotFoundException e) {
            SourceChecker.errorAbort("Could not load class '" + cn + "' for field '" + name +
                    "' in annotation " + anno);
            return null; // dead code
        }
    }

    /**
     * Update a mapping from some key to a set of AnnotationMirrors.
     * If the key already exists in the mapping and the new qualifier
     * is in the same qualifier hierarchy as any of the existing qualifiers,
     * do nothing and return false.
     * If the key already exists in the mapping and the new qualifier
     * is not in the same qualifier hierarchy as any of the existing qualifiers,
     * add the qualifier to the existing set and return true.
     * If the key does not exist in the mapping, add the new qualifier as a
     * singleton set and return true.
     *
     * @param map The mapping to modify.
     * @param key The key to update.
     * @param newQual The value to add.
     * @return Whether there was a qualifier hierarchy collision.
     */
    public static <T> boolean updateMappingToMutableSet(QualifierHierarchy qualHierarchy,
            Map<T, Set<AnnotationMirror>> map,
            T key, AnnotationMirror newQual) {

        if (!map.containsKey(key)) {
            Set<AnnotationMirror> set = AnnotationUtils.createAnnotationSet();
            set.add(newQual);
            map.put(key, set);
        } else {
            Set<AnnotationMirror> prevs = map.get(key);
            for (AnnotationMirror p : prevs) {
                if (AnnotationUtils.areSame(qualHierarchy.getTopAnnotation(p),
                        qualHierarchy.getTopAnnotation(newQual))) {
                    return false;
                }
            }
            prevs.add(newQual);
            map.put(key, prevs);
        }
        return true;
    }

    /**
     *
     * @see #updateMappingToMutableSet(QualifierHierarchy, Map, Object, AnnotationMirror)
     */
    public static <T> void updateMappingToImmutableSet(Map<T, Set<AnnotationMirror>> map,
            T key, Set<AnnotationMirror> newQual) {

        Set<AnnotationMirror> result = AnnotationUtils.createAnnotationSet();
        // TODO: if T is also an AnnotationMirror, should we use areSame?
        if (!map.containsKey(key)) {
            result.addAll(newQual);
        } else {
            result.addAll(map.get(key));
            result.addAll(newQual);
        }
        map.put(key, Collections.unmodifiableSet(result));
    }
}
