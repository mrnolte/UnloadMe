package de.bremen.unloadme;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// T: Signature type
// R: Type for Replacement, e.g. String
public class SignatureMapper<T, R> {
	
	private final Set<T> originalSignature;
	private final Set<T> extendedSignature;
	private final Map<R, Map<Object, T>> extensionMap = new HashMap<>();
	private final Map<R, AtomicLong> freshExtensionIndexMap = new HashMap<>();
	
	public SignatureMapper(final Stream<T> originalSignature) {
		this.originalSignature = originalSignature.collect(Collectors.toSet());
		extendedSignature = new HashSet<>(this.originalSignature);
	}
	
	public synchronized boolean containsInExtendedSignature(final T extension) {
		return extendedSignature.contains(extension);
	}
	
	public synchronized boolean containsInOriginalSignature(final T entity) {
		return originalSignature.contains(entity);
	}
	
	@SuppressWarnings("unchecked")
	protected final synchronized <T2 extends T> T2 extendDepending(final R reason, final Object dependsOn,
			final BiFunction<R, Long, T2> entityConstructor) {
		return (T2) extensionMap.computeIfAbsent(reason, r -> new HashMap<>()).computeIfAbsent(dependsOn, o -> {
			long i = 0;
			T2 extension;
			
			do {
				extension = entityConstructor.apply(reason, i++);
			} while (containsInExtendedSignature(extension));
			
			extendedSignature.add(extension);
			
			return extension;
		});
		
	}
	
	public Stream<T> extendedSignature() {
		return extendedSignature.stream();
	}
	
	protected synchronized final <T2 extends T> T2 extendFresh(final R reason,
			final BiFunction<R, Long, T2> entityConstructor) {
		final AtomicLong i = freshExtensionIndexMap.computeIfAbsent(reason, r -> new AtomicLong());
		T2 extension;
		
		do {
			extension = entityConstructor.apply(reason, i.getAndIncrement());
		} while (containsInExtendedSignature(extension));
		
		extendedSignature.add(extension);
		
		return extension;
	}
	
	public Stream<T> originalSignature() {
		return originalSignature.stream();
	}
	
}
