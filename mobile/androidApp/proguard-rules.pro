# App-specific R8 rules. Libraries ship their own consumer rules.

# Navigation finds a tab by its graph's route class (NavDestination.hasRoute(KClass)). The tab graph
# routes are empty @Serializable data objects, which R8 would otherwise merge into one class, so one
# tab opened another (Staff opened Stock). Keeping them stops the merge; names may still be shortened.
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.onemind.**.navigation.** { *; }
