package org.example.lifecomposer.importer;

/**
 * One import error carried by {@link ImportReport}.
 *
 * <p>This is a deliberate {@code record}, not an unfinished class: an import
 * error is an immutable value object made only of its four components, so the
 * compiler-generated canonical constructor, accessors, {@code equals},
 * {@code hashCode} and {@code toString} are exactly the behaviour the import
 * pipeline needs. Hand-written getters or extra fields would add code without
 * adding capability.
 *
 * <p>Component contract (locked by {@code ImportErrorTest} and by the report
 * JSON field names {@code file}, {@code record}, {@code businessKey},
 * {@code reason}):
 * <ul>
 *   <li>{@code file} — source file, directory or synthetic stage name; not null</li>
 *   <li>{@code record} — 1-based record number when the source is a JSON array;
 *       {@code 0} means "no usable 1-based array index for this error". The
 *       importers use {@code 0} for three different shapes:
 *       <ul>
 *         <li>file/path-level errors such as an unreadable file or a root node
 *             that is not an array/object ({@code businessKey} is usually null)</li>
 *         <li>keyed-object member errors for capability tags and reference
 *             dictionaries, where there is no array index and
 *             {@code businessKey} carries the object key</li>
 *         <li>synthetic processing stages such as {@code file="embedding"},
 *             where the stage name replaces the file and {@code businessKey}
 *             carries the chunk id</li>
 *       </ul>
 *   </li>
 *   <li>{@code businessKey} — importer-specific key when one is known; may be
 *       {@code null} for file-level or unparsable records</li>
 *   <li>{@code reason} — human-readable failure reason for the report</li>
 * </ul>
 *
 * <p>If a future import requirement needs error codes, severity or recovery
 * hints, that should be a separately versioned report DTO rather than
 * speculative fields on this value object.
 */
public record ImportError(String file, int record, String businessKey, String reason) {
}
