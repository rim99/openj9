/*[INCLUDE-IF Sidecar16]*/

package java.lang;

/*******************************************************************************
 * Copyright (c) 1998, 2017 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *******************************************************************************/

import java.io.Serializable;
import java.util.Arrays;
import java.util.Properties;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InvalidObjectException;

/*[IF Sidecar19-SE]*/
import java.util.stream.IntStream;
/*[ENDIF]*/

/**
 * StringBuffer is a variable size contiguous indexable array of characters.
 * The length of the StringBuffer is the number of characters it contains.
 * The capacity of the StringBuffer is the number of characters it can hold.
 * <p>
 * Characters may be inserted at any position up to the length of the
 * StringBuffer, increasing the length of the StringBuffer. Characters at any
 * position in the StringBuffer may be replaced, which does not affect the
 * StringBuffer length.
 * <p>
 * The capacity of a StringBuffer may be specified when the StringBuffer is
 * created. If the capacity of the StringBuffer is exceeded, the capacity
 * is increased.
 *
 * @author		OTI
 * @version		initial
 *
 * @see			String
 */
 
/*[IF]*/
/*
 * Modification history:
 *	PDS: 990614 - added comments, no code changes
 *	PDS: 990616 - 1FDRSWI: J9JCL:ALL - Plum Hall failures.
 *  BML: 020320 - Added memoryspace awareness ([IF ResMan]) 
 */
/*[ENDIF]*/

public final class StringBuffer extends AbstractStringBuilder implements Serializable, CharSequence, Appendable {
	private static final long serialVersionUID = 3388685877147921107L;
	
	private static final int INITIAL_SIZE = 16;
	
	/*[IF Sidecar19-SE]*/
	/**
	 * The maximum capacity of a StringBuffer.
	 */
	private static final int MAX_CAPACITY = Integer.MAX_VALUE / 2;
	/*[ENDIF]*/
	
	private static boolean TOSTRING_COPY_BUFFER_ENABLED = false;
	
	// Used to access compression related helper methods
	private static final com.ibm.jit.JITHelpers helpers = com.ibm.jit.JITHelpers.getHelpers();
	
	// Represents the bit in count field to test for whether this StringBuffer backing array is not compressed
	// under String compression mode. This bit is not used when String compression is disabled.
	private static final int uncompressedBit = 0x80000000;
	
	/*[IF !Sidecar19-SE]*/
	// Represents the bit in capacity field to test for whether this StringBuffer backing array is shared.
	private static final int sharedBit = 0x80000000;
	/*[ENDIF]*/
	
	private static final java.io.ObjectStreamField serialPersistentFields[] = new java.io.ObjectStreamField[] {
		new java.io.ObjectStreamField("count", Integer.TYPE), //$NON-NLS-1$
		new java.io.ObjectStreamField("value", char[].class), //$NON-NLS-1$
		new java.io.ObjectStreamField("shared", Boolean.TYPE), //$NON-NLS-1$
	};
	
	private int count;
	/*[IF Sidecar19-SE]*/
	private byte[] value;
	private boolean shared;
	/*[ELSE]*/
	private char[] value;
	private int capacity;
	/*[ENDIF]*/
	
	private void decompress(int min) {
		int currentLength = lengthInternalUnsynchronized();
		int currentCapacity = capacityInternal();
		
		/*[IF Sidecar19-SE]*/
		byte[] newValue = null;
		/*[ELSE]*/
		char[] newValue = null;
		/*[ENDIF]*/
		
		if (min > currentCapacity) {
			int twice = (currentCapacity << 1) + 2;
			
			/*[IF Sidecar19-SE]*/
			newValue = new byte[(min > twice ? min : twice) * 2];
			/*[ELSE]*/
			newValue = new char[min > twice ? min : twice];
			/*[ENDIF]*/
		} else {
			/*[IF Sidecar19-SE]*/
			newValue = new byte[currentCapacity * 2];
			/*[ELSE]*/
			newValue = new char[currentCapacity];
			/*[ENDIF]*/
		}

		String.decompress(value, 0, newValue, 0, currentLength);
		
		count = count | uncompressedBit;
		value = newValue;
		/*[IF !Sidecar19-SE]*/
		capacity = newValue.length;
		/*[ENDIF]*/
		
		String.initCompressionFlag();
	}
	
/**
 * Constructs a new StringBuffer using the default capacity.
 */
public StringBuffer() {
	this(INITIAL_SIZE);
}

/**
 * Constructs a new StringBuffer using the specified capacity.
 *
 * @param		capacity	the initial capacity
 */
public StringBuffer(int capacity) {
	if (String.enableCompression) {
		/*[IF Sidecar19-SE]*/
		if (capacity <= MAX_CAPACITY) {
			value = new byte[capacity];
		} else {
			/*[MSG "K05df", "Unable to allocate an array of the specified capacity. The maximum supported capacity is Integer.MAX_VALUE / 2."]*/
			throw new OutOfMemoryError(com.ibm.oti.util.Msg.getString("K05df")); //$NON-NLS-1$
		}
		/*[ELSE]*/
		if (capacity == Integer.MAX_VALUE) {
			value = new char[(capacity / 2) + 1];
		} else {
			value = new char[(capacity + 1) / 2];
		}
		/*[ENDIF]*/
	} else {
		/*[IF Sidecar19-SE]*/
		if (capacity <= MAX_CAPACITY) {
			value = new byte[capacity * 2];
		} else {
			/*[MSG "K05df", "Unable to allocate an array of the specified capacity. The maximum supported capacity is Integer.MAX_VALUE / 2."]*/
			throw new OutOfMemoryError(com.ibm.oti.util.Msg.getString("K05df")); //$NON-NLS-1$
		}
		/*[ELSE]*/
		value = new char[capacity];
		/*[ENDIF]*/
	}
	
	/*[IF !Sidecar19-SE]*/
	this.capacity = capacity;
	/*[ENDIF]*/
}

/**
 * Constructs a new StringBuffer containing the characters in
 * the specified string and the default capacity.
 *
 * @param		string	the initial contents of this StringBuffer
 * @exception	NullPointerException when string is null
 */
public StringBuffer (String string) {
	int stringLength = string.lengthInternal();
	
	int newLength = stringLength + INITIAL_SIZE;
	
	if (String.enableCompression) {
		if (string.isCompressed ()) {
			/*[IF Sidecar19-SE]*/
			value = new byte[newLength];
			/*[ELSE]*/
			value = new char[(newLength + 1) / 2];
			/*[ENDIF]*/

			string.getBytes(0, stringLength, value, 0);
			
			/*[IF !Sidecar19-SE]*/
			capacity = newLength;
			/*[ENDIF]*/
			
			count = stringLength;
		} else {
			/*[IF Sidecar19-SE]*/
			value = new byte[newLength * 2];
			/*[ELSE]*/
			value = new char[newLength];
			/*[ENDIF]*/

			string.getChars(0, stringLength, value, 0);

			/*[IF !Sidecar19-SE]*/
			capacity = newLength;
			/*[ENDIF]*/
			
			count = stringLength | uncompressedBit;
			
			String.initCompressionFlag();
		}
	} else {
		/*[IF Sidecar19-SE]*/
		value = new byte[newLength * 2];
		
		string.getChars(0, stringLength, value, 0);
		
		count = stringLength;
		/*[ELSE]*/
		value = new char[newLength];
		
		string.getChars(0, stringLength, value, 0);
		
		capacity = newLength;
		
		count = stringLength;
		/*[ENDIF]*/
	}
}

/**
 * Adds the character array to the end of this StringBuffer.
 *
 * @param		chars	the character array
 * @return		this StringBuffer
 *
 * @exception	NullPointerException when chars is null
 */
public synchronized StringBuffer append (char[] chars) {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	int newLength = currentLength + chars.length;
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && String.compressible(chars, 0, chars.length)) {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			String.compress(chars, 0, value, currentLength, chars.length);
			
			count = newLength;
		} else {
			// Check if the StringBuffer is compressed
			if (count >= 0) {
				decompress(newLength);
			}
			
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			/*[IF Sidecar19-SE]*/
			String.decompressedArrayCopy(chars, 0, value, currentLength, chars.length);
			/*[ELSE]*/
			System.arraycopy(chars, 0, value, currentLength, chars.length);
			/*[ENDIF]*/
			
			count = newLength | uncompressedBit;
		}
	} else {
		if (newLength > currentCapacity) {
			ensureCapacityImpl(newLength);
		}
		
		/*[IF Sidecar19-SE]*/
		String.decompressedArrayCopy(chars, 0, value, currentLength, chars.length);
		/*[ELSE]*/
		System.arraycopy(chars, 0, value, currentLength, chars.length);
		/*[ENDIF]*/
		
		count = newLength;
	}
	
	return this;
}

/**
 * Adds the specified sequence of characters to the end of
 * this StringBuffer.
 *
 * @param		chars	a character array
 * @param		start	the starting offset
 * @param		length	the number of characters
 * @return		this StringBuffer
 *
 * @exception	IndexOutOfBoundsException when <code>length < 0, start < 0</code> or
 *				<code>start + length > chars.length</code>
 * @exception	NullPointerException when chars is null
 */
public synchronized StringBuffer append (char chars[], int start, int length) {
	if (start >= 0 && 0 <= length && length <= chars.length - start) {
		int currentLength = lengthInternalUnsynchronized();
		int currentCapacity = capacityInternal();
		
		int newLength = currentLength + length;
		
		if (String.enableCompression) {
			// Check if the StringBuffer is compressed
			if (count >= 0 && String.compressible(chars, start, length)) {
				if (newLength > currentCapacity) {
					ensureCapacityImpl(newLength);
				}
				
				String.compress(chars, start, value, currentLength, length);
				
				count = newLength;
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(newLength);				  
				}
				
				if (newLength > currentCapacity) {
					ensureCapacityImpl(newLength);
				}
				
				/*[IF Sidecar19-SE]*/
				String.decompressedArrayCopy(chars, start, value, currentLength, length);
				/*[ELSE]*/
				System.arraycopy(chars, start, value, currentLength, length);
				/*[ENDIF]*/
				
				count = newLength | uncompressedBit;
			}
		} else {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			/*[IF Sidecar19-SE]*/
			String.decompressedArrayCopy(chars, start, value, currentLength, length);
			/*[ELSE]*/
			System.arraycopy(chars, start, value, currentLength, length);
			/*[ENDIF]*/
			
			count = newLength;
		}
		
		return this;
	} else {
		throw new StringIndexOutOfBoundsException();
	}
}

/*[IF Sidecar19-SE]*/
synchronized StringBuffer append (byte[] chars, int start, int length, boolean compressed) {
/*[ELSE]*/
synchronized StringBuffer append (char[] chars, int start, int length, boolean compressed) {
/*[ENDIF]*/
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	int newLength = currentLength + length;
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && compressed) {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			String.compressedArrayCopy(chars, start, value, currentLength, length);
			
			count = newLength;
		} else {
			// Check if the StringBuffer is compressed
			if (count >= 0) {
				decompress(newLength);				
			}
			
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			if (compressed) {
				String.decompress(chars, start, value, currentLength, length);
			} else {
				/*[IF Sidecar19-SE]*/
				String.decompressedArrayCopy(chars, start, value, currentLength, length);
				/*[ELSE]*/
				System.arraycopy(chars, start, value, currentLength, length);
				/*[ENDIF]*/
			}
			
			count = newLength | uncompressedBit;
		}
	} else {
		if (newLength > currentCapacity) {
			ensureCapacityImpl(newLength);
		}
		
		if (compressed) {
			String.decompress(chars, start, value, currentLength, length);
		} else {
			/*[IF Sidecar19-SE]*/
			String.decompressedArrayCopy(chars, start, value, currentLength, length);
			/*[ELSE]*/
			System.arraycopy(chars, start, value, currentLength, length);
			/*[ENDIF]*/
		}
		
		count = newLength;
	}
	
	return this;
}

/**
 * Adds the specified character to the end of
 * this StringBuffer.
 *
 * @param		ch	a character
 * @return		this StringBuffer
 */
public synchronized StringBuffer append(char ch) {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	int newLength = currentLength + 1;
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && ch <= 255) {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			helpers.putByteInArrayByIndex(value, currentLength, (byte) ch);
			
			count = newLength;
		} else {
			// Check if the StringBuffer is compressed
			if (count >= 0) {
				decompress(newLength);
			}
			
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			/*[IF Sidecar19-SE]*/
			helpers.putCharInArrayByIndex(value, currentLength, (char) ch);
			/*[ELSE]*/
			value[currentLength] = ch;
			/*[ENDIF]*/
			
			count = newLength | uncompressedBit;
		}
	} else {
		if (newLength > currentCapacity) {
			ensureCapacityImpl(newLength);
		}
		
		/*[IF Sidecar19-SE]*/
		helpers.putCharInArrayByIndex(value, currentLength, (char) ch);
		/*[ELSE]*/
		value[currentLength] = ch;
		/*[ENDIF]*/
		
		count = newLength;
	}
	
	return this;
}

/**
 * Adds the string representation of the specified double to the
 * end of this StringBuffer.
 *
 * @param		value	the double
 * @return		this StringBuffer
 */
public StringBuffer append (double value) {
	return append (String.valueOf (value));
}

/**
 * Adds the string representation of the specified float to the
 * end of this StringBuffer.
 *
 * @param		value	the float
 * @return		this StringBuffer
 */
public StringBuffer append (float value) {
	return append (String.valueOf (value));
}

/**
 * Adds the string representation of the specified integer to the
 * end of this StringBuffer.
 *
 * @param		value	the integer
 * @return		this StringBuffer
 */
public StringBuffer append (int value) {
	return append(Integer.toString(value));
}

/**
 * Adds the string representation of the specified long to the
 * end of this StringBuffer.
 *
 * @param		value	the long
 * @return		this StringBuffer
 */
public StringBuffer append (long value) {
	return append(Long.toString(value));
}

/**
 * Adds the string representation of the specified object to the
 * end of this StringBuffer.
 *
 * @param		value	the object
 * @return		this StringBuffer
 */
public StringBuffer append (Object value) {
	return append (String.valueOf (value));
}

/**
 * Adds the specified string to the end of this StringBuffer.
 *
 * @param		string	the string
 * @return		this StringBuffer
 */
public synchronized StringBuffer append (String string) {
	if (string == null) {
		string = "null"; //$NON-NLS-1$
	}
	
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	int stringLength = string.lengthInternal();
	
	int newLength = currentLength + stringLength;
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && string.isCompressed ()) {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			string.getBytes(0, stringLength, value, currentLength);
			
			count = newLength;
		} else {
			// Check if the StringBuffer is compressed
			if (count >= 0) {
				decompress(newLength);
			}
			
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			string.getChars(0, stringLength, value, currentLength);
			
			count = newLength | uncompressedBit;
		}
	} else {
		if (newLength > currentCapacity) {
			ensureCapacityImpl(newLength);
		}
		
		string.getChars(0, stringLength, value, currentLength);
		
		count = newLength;
	}
	
	return this;
}

/**
 * Adds the string representation of the specified boolean to the
 * end of this StringBuffer.
 *
 * @param		value	the boolean
 * @return		this StringBuffer
 */
public StringBuffer append (boolean value) {
	return append (String.valueOf (value));
}

/**
 * Answers the number of characters this StringBuffer can hold without
 * growing.
 *
 * @return		the capacity of this StringBuffer
 *
 * @see			#ensureCapacity
 * @see			#length
 */
public int capacity() {
	return capacityInternal();
}

/**
 * Answers the number of characters this StringBuffer can hold without growing. This method is to be used internally
 * within the current package whenever possible as the JIT compiler will take special precaution to avoid generating
 * HCR guards for calls to this method.
 *
 * @return the capacity of this StringBuffer
 *
 * @see #ensureCapacity
 * @see #length
 */
int capacityInternal() {
	/*[IF Sidecar19-SE]*/
	if (String.enableCompression && count >= 0) {
		return value.length;
	} else {
		return value.length / 2;
	}
	/*[ELSE]*/
	return capacity & ~sharedBit;
	/*[ENDIF]*/
}

/**
 * Answers the character at the specified offset in this StringBuffer.
 *
 * @param 		index	the zero-based index in this StringBuffer
 * @return		the character at the index
 *
 * @exception	IndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index >= length()</code>
 */
public synchronized char charAt(int index) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (index >= 0 && index < currentLength) {
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			return helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, index));
		} else {
			/*[IF Sidecar19-SE]*/
			return helpers.getCharFromArrayByIndex(value, index);
			/*[ELSE]*/
			return value[index];
			/*[ENDIF]*/
		}
	}
	
	throw new StringIndexOutOfBoundsException(index);
}

/**
 * Deletes a range of characters.
 *
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>start < 0, start > end</code> or
 *				<code>end > length()</code>
 */
public synchronized StringBuffer delete(int start, int end) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (start >= 0) {
		if (end > currentLength) {
			end = currentLength;
		}
		
		if (end > start) {
			int numberOfTailChars = currentLength - end;

			/*[PR CMVC 104709] Optimize String sharing for more performance */
			try {
				// Check if the StringBuffer is not shared
				/*[IF Sidecar19-SE]*/
				if (!shared) {
				/*[ELSE]*/
				if (capacity >= 0) {
				/*[ENDIF]*/
					if (numberOfTailChars > 0) {
						// Check if the StringBuffer is compressed
						if (String.enableCompression && count >= 0) {
							String.compressedArrayCopy(value, end, value, start, numberOfTailChars);
						} else {
							/*[IF Sidecar19-SE]*/
							String.decompressedArrayCopy(value, end, value, start, numberOfTailChars);
							/*[ELSE]*/
							System.arraycopy(value, end, value, start, numberOfTailChars);
							/*[ENDIF]*/
						}
					}
				} else {
					/*[IF Sidecar19-SE]*/
					byte[] newData = new byte[value.length];
					/*[ELSE]*/
					char[] newData = new char[value.length];
					/*[ENDIF]*/
					
					// Check if the StringBuffer is compressed
					if (String.enableCompression && count >= 0) {
						if (start > 0) {
							String.compressedArrayCopy(value, 0, newData, 0, start);
						}
						
						if (numberOfTailChars > 0) {
							String.compressedArrayCopy(value, end, newData, start, numberOfTailChars);
						}
					} else {
						if (start > 0) {
							/*[IF Sidecar19-SE]*/
							String.decompressedArrayCopy(value, 0, newData, 0, start);
							/*[ELSE]*/
							System.arraycopy(value, 0, newData, 0, start);
							/*[ENDIF]*/
						}
						
						if (numberOfTailChars > 0) {
							/*[IF Sidecar19-SE]*/
							String.decompressedArrayCopy(value, end, newData, start, numberOfTailChars);
							/*[ELSE]*/
							System.arraycopy(value, end, newData, start, numberOfTailChars);
							/*[ENDIF]*/
						}
					}
					
					value = newData;
					
					/*[IF Sidecar19-SE]*/
					shared = false;
					/*[ELSE]*/
					capacity = capacity & ~sharedBit;
					/*[ENDIF]*/
				}
			} catch (IndexOutOfBoundsException e) {
				throw new StringIndexOutOfBoundsException();
			}
			
			if (String.enableCompression) {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					count = currentLength - (end - start);
				} else {
					count = (currentLength - (end - start)) | uncompressedBit;
					
					String.initCompressionFlag();
				}
			} else {
				count = currentLength - (end - start);
			}
			
			return this;
		}
		
		if (start == end) {
			return this;
		}
	}
	
	throw new StringIndexOutOfBoundsException();
}

/**
 * Deletes a single character
 *
 * @param		location	the offset of the character to delete
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>location < 0</code> or
 *				<code>location >= length()</code>
 */
public synchronized StringBuffer deleteCharAt(int location) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (currentLength != 0) {
		return delete (location, location + 1);
	} else {
		throw new StringIndexOutOfBoundsException ();
	}
}

/**
 * Ensures that this StringBuffer can hold the specified number of characters
 * without growing.
 *
 * @param		min	 the minimum number of elements that this
 *				StringBuffer will hold before growing
 */
public synchronized void ensureCapacity(int min) {
	int currentCapacity = capacityInternal();
	
	if (min > currentCapacity) {
		ensureCapacityImpl(min);
	}
}

private void ensureCapacityImpl(int min) {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	int newCapacity = (currentCapacity << 1) + 2;

	int newLength = min > newCapacity ? min : newCapacity;
	
	// Check if the StringBuilder is compressed
	if (String.enableCompression && count >= 0) {
		/*[IF Sidecar19-SE]*/
		byte[] newData = new byte[newLength];
		/*[ELSE]*/
		char[] newData = new char[(newLength + 1) / 2];
		/*[ENDIF]*/
		
		String.compressedArrayCopy(value, 0, newData, 0, currentLength);
		
		value = newData;
		
	} else {
		/*[IF Sidecar19-SE]*/
		byte[] newData = new byte[newLength * 2];
		
		String.decompressedArrayCopy(value, 0, newData, 0, currentLength);
		/*[ELSE]*/
		char[] newData = new char[newLength];
		
		System.arraycopy(value, 0, newData, 0, currentLength);
		/*[ENDIF]*/
		
		value = newData;
	}
	
	/*[IF !Sidecar19-SE]*/
	capacity = newLength;
	/*[ENDIF]*/
}

/**
 * Copies the specified characters in this StringBuffer to the character array
 * starting at the specified offset in the character array.
 *
 * @param		start	the starting offset of characters to copy
 * @param		end	the ending offset of characters to copy
 * @param		buffer	the destination character array
 * @param		index	the starting offset in the character array
 *
 * @exception	IndexOutOfBoundsException when <code>start < 0, end > length(),
 *				start > end, index < 0, end - start > buffer.length - index</code>
 * @exception	NullPointerException when buffer is null
 */
public synchronized void getChars(int start, int end, char[] buffer, int index) {
	try {
		int currentLength = lengthInternalUnsynchronized();
		
		if (start <= currentLength && end <= currentLength) {
			// Note that we must explicitly check all the conditions because we are not using System.arraycopy which would
			// have implicitly checked these conditions
			if (start >= 0 && start <= end && index >= 0 && end - start <= buffer.length - index) {
				// Check if the StringBuffer is compressed
				if (String.enableCompression && count >= 0) {
					String.decompress(value, start, buffer, index, end - start);
					
					return;
				} else {
					/*[IF Sidecar19-SE]*/
					String.decompressedArrayCopy(value, start, buffer, index, end - start);
					/*[ELSE]*/
					System.arraycopy(value, start, buffer, index, end - start);
					/*[ENDIF]*/
				
					return;
				}
			}
		}
	/*[IF]*/
	// TODO : Is this IOOB ever possible given the above check?
	/*[ENDIF]*/
	} catch(IndexOutOfBoundsException e) {
		// Void
	}
	
	throw new StringIndexOutOfBoundsException ();
}

/**
 * Inserts the character array at the specified offset in this StringBuffer.
*
 * @param		index	the index at which to insert
 * @param		chars	the character array to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 * @exception	NullPointerException when chars is null
 */
public synchronized StringBuffer insert(int index, char[] chars) {
	int currentLength = lengthInternalUnsynchronized();
	
	/*[PR 101359] insert(-1, (char[])null) should throw StringIndexOutOfBoundsException */
	if (0 <= index && index <= currentLength) {
		move(chars.length, index);
		
		if (String.enableCompression) {
			// Check if the StringBuffer is compressed
			if (count >= 0 && String.compressible(chars, 0, chars.length)) {
				String.compress(chars, 0, value, index, chars.length);
				
				count = currentLength + chars.length;
				
				return this;
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(value.length);
				}
				
				/*[IF Sidecar19-SE]*/
				String.decompressedArrayCopy(chars, 0, value, index, chars.length);
				/*[ELSE]*/
				System.arraycopy(chars, 0, value, index, chars.length);
				/*[ENDIF]*/
				
				count = (currentLength + chars.length) | uncompressedBit;
				
				return this;
			}
		} else {
			/*[IF Sidecar19-SE]*/
			String.decompressedArrayCopy(chars, 0, value, index, chars.length);
			/*[ELSE]*/
			System.arraycopy(chars, 0, value, index, chars.length);
			/*[ENDIF]*/
			
			count = currentLength + chars.length;
			
			return this;
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Inserts the specified sequence of characters at the
 * specified offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		chars	a character array
 * @param		start	the starting offset
 * @param		length	the number of characters
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>length < 0, start < 0,</code>
 *				<code>start + length > chars.length, index < 0</code> or
 *				<code>index > length()</code>
 * @exception	NullPointerException when chars is null
 */
public synchronized StringBuffer insert(int index, char[] chars, int start, int length) {
	int currentLength = lengthInternalUnsynchronized();
	
	/*[PR 101359] insert(-1, (char[])null, x, x) should throw StringIndexOutOfBoundsException */
	if (0 <= index && index <= currentLength) {
		if (start >= 0 && 0 <= length && length <= chars.length - start) {
			move(length, index);
			
			if (String.enableCompression) {
				// Check if the StringBuffer is compressed
				if (count >= 0 && String.compressible(chars, start, length)) {
					String.compress(chars, start, value, index, length);
					
					count = currentLength + length;
					
					return this;
				} else {
					// Check if the StringBuffer is compressed
					if (count >= 0) {
						decompress(value.length);
					}
					
					/*[IF Sidecar19-SE]*/
					String.decompressedArrayCopy(chars, start, value, index, length);
					/*[ELSE]*/
					System.arraycopy(chars, start, value, index, length);
					/*[ENDIF]*/
					
					count = (currentLength + length) | uncompressedBit;
					
					return this;
				}
			} else {
				/*[IF Sidecar19-SE]*/
				String.decompressedArrayCopy(chars, start, value, index, length);
				/*[ELSE]*/
				System.arraycopy(chars, start, value, index, length);
				/*[ENDIF]*/
				
				count = currentLength + length;
				
				return this;
			}
		} else {
			throw new StringIndexOutOfBoundsException();
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/*[IF Sidecar19-SE]*/
synchronized StringBuffer insert(int index, byte[] chars, int start, int length, boolean compressed) {
/*[ELSE]*/
synchronized StringBuffer insert(int index, char[] chars, int start, int length, boolean compressed) {
/*[ENDIF]*/
	int currentLength = lengthInternalUnsynchronized();
	
	move(length, index);
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && compressed) {
			String.compressedArrayCopy(chars, start, value, index, length);
			
			count = currentLength + length;
			
			return this;
		} else {
			if (count >= 0) {
				decompress(value.length);
			}
			
			/*[IF Sidecar19-SE]*/
			String.decompressedArrayCopy(chars, start, value, index, length);
			/*[ELSE]*/
			System.arraycopy(chars, start, value, index, length);
			/*[ENDIF]*/
			
			count = (currentLength + length) | uncompressedBit;
			
			return this;
		}
	} else {
		/*[IF Sidecar19-SE]*/
		String.decompressedArrayCopy(chars, start, value, index, length);
		/*[ELSE]*/
		System.arraycopy(chars, start, value, index, length);
		/*[ENDIF]*/
		
		count = currentLength + length;
		
		return this;
	}
}

/**
 * Inserts the character at the specified offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		ch	the character to insert
 * @return		this StringBuffer
 *
 * @exception	IndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public synchronized StringBuffer insert(int index, char ch) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (0 <= index && index <= currentLength) {
		move(1, index);
		
		if (String.enableCompression ) {
			// Check if the StringBuffer is compressed
			if (count >= 0 && ch <= 255) {
				helpers.putByteInArrayByIndex(value, index, (byte) ch);
				
				count = currentLength + 1;
				
				return this;
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(value.length);
				}
				
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(value, index, (char) ch);
				/*[ELSE]*/
				value[index] = ch;
				/*[ENDIF]*/
				
				count = (currentLength + 1) | uncompressedBit;
				
				return this;
			}
		} else {
			/*[IF Sidecar19-SE]*/
			helpers.putCharInArrayByIndex(value, index, (char) ch);
			/*[ELSE]*/
			value[index] = ch;
			/*[ENDIF]*/
			
			count = currentLength + 1;
			
			return this;
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Inserts the string representation of the specified double at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the double to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, double value) {
	return insert(index, String.valueOf(value));
}

/**
 * Inserts the string representation of the specified float at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the float to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, float value) {
	return insert(index, String.valueOf(value));
}

/**
 * Inserts the string representation of the specified integer at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the integer to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, int value) {
	return insert(index, Integer.toString(value));
}

/**
 * Inserts the string representation of the specified long at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the long to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, long value) {
	return insert(index, Long.toString(value));
}

/**
 * Inserts the string representation of the specified object at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the object to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, Object value) {
	return insert(index, String.valueOf(value));
}

/**
 * Inserts the string at the specified offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		string	the string to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public synchronized StringBuffer insert(int index, String string) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (0 <= index && index <= currentLength) {
		if (string == null) {
			string = "null"; //$NON-NLS-1$
		}
		
		int stringLength = string.lengthInternal();
		
		move(stringLength, index);
		
		if (String.enableCompression) {
			// Check if the StringBuffer is compressed
			if (count >= 0 && string.isCompressed()) {
				string.getBytes(0, stringLength, value, index);
				
				count = currentLength + stringLength;
				
				return this;
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(value.length);
				}
				
				string.getChars(0, stringLength, value, index);
				
				count = (currentLength + stringLength) | uncompressedBit;
				
				return this;
			}
		} else {
			string.getChars(0, stringLength, value, index);
			
			count = currentLength + stringLength;
			
			return this;
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Inserts the string representation of the specified boolean at the specified
 * offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		value	the boolean to insert
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 */
public StringBuffer insert(int index, boolean value) {
	return insert(index, String.valueOf(value));
}

/**
 * Answers the size of this StringBuffer.
 *
 * @return		the number of characters in this StringBuffer
 */
public synchronized int length() {
	return lengthInternalUnsynchronized();
}

/**
 * Answers the size of this StringBuffer. This is an unsynchronized private method and is meant to be called only
 * from methods of this class which have already synchronized on the this StringBuffer object.
 *
 * The JIT compiler will take special precaution to avoid generating HCR guards for calls to this method.
 * @return the number of characters in this StringBuffer
 */
private int lengthInternalUnsynchronized() {
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0) {
			return count;
		} else {
			return count & ~uncompressedBit;
		}
	} else {
		return count;
	}
}

private void move(int size, int index) {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		int newLength;
		
		if (currentCapacity - currentLength >= size) {
			// Check if the StringBuffer is not shared
			/*[IF Sidecar19-SE]*/
			if (!shared) {
			/*[ELSE]*/
			if (capacity >= 0) {
			/*[ENDIF]*/
				String.compressedArrayCopy(value, index, value, index + size, currentLength - index);
				
				return;
			}
			
			newLength = currentCapacity;
		} else {
			newLength = Integer.max(currentLength + size, (currentCapacity << 1) + 2);
		}
		
		/*[IF Sidecar19-SE]*/
		byte[] newData = new byte[newLength];
		/*[ELSE]*/
		char[] newData = new char[(newLength + 1) / 2];
		/*[ENDIF]*/
		
		String.compressedArrayCopy(value, 0, newData, 0, index);
		String.compressedArrayCopy(value, index, newData, index + size, currentLength - index);
		
		value = newData;
		
		/*[IF !Sidecar19-SE]*/
		capacity = newLength;
		/*[ENDIF]*/
	} else {
		int newLength;
		
		if (currentCapacity - currentLength >= size) {
			// Check if the StringBuffer is not shared
			/*[IF Sidecar19-SE]*/
			if (!shared) {
				String.decompressedArrayCopy(value, index, value, index + size, currentLength - index);
			/*[ELSE]*/
			if (capacity >= 0) {
				System.arraycopy(value, index, value, index + size, currentLength - index);
			/*[ENDIF]*/
				
				return;
			}
			
			newLength = currentCapacity;
		} else {
			newLength = Integer.max(currentLength + size, (currentCapacity << 1) + 2);
		}
		
		/*[IF Sidecar19-SE]*/
		byte[] newData = new byte[newLength * 2];
		
		String.decompressedArrayCopy(value, 0, newData, 0, index);
		String.decompressedArrayCopy(value, index, newData, index + size, currentLength - index);
		
		value = newData;
		/*[ELSE]*/
		char[] newData = new char[newLength];
		
		System.arraycopy(value, 0, newData, 0, index);
		System.arraycopy(value, index, newData, index + size, currentLength - index);
		
		value = newData;
		
		capacity = newLength;
		/*[ENDIF]*/
	}
}

/**
 * Replace a range of characters with the characters in the specified String.
 *
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @param		string	a String
 * @return		this StringBuffer
 *
 * @exception	StringIndexOutOfBoundsException when <code>start < 0</code> or
 *				<code>start > end</code>
 */
public synchronized StringBuffer replace(int start, int end, String string) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0 && string.isCompressed()) {
			if (start >= 0) {
				if (end > currentLength) {
					end = currentLength;
				}
				
				if (end > start) {
					int size = string.lengthInternal();
					
					// Difference between the substring we wish to replace and the size of the string parameter
					int difference = end - start - size;
					
					if (difference > 0) {
						// Check if the StringBuffer is not shared
						/*[IF Sidecar19-SE]*/
						if (!shared) {
						/*[ELSE]*/
						if (capacity >= 0) {
						/*[ENDIF]*/
							String.compressedArrayCopy(value, end, value, start + size, currentLength - end);
						} else {
							/*[IF Sidecar19-SE]*/
							byte[] newData = new byte[value.length];
							/*[ELSE]*/
							char[] newData = new char[value.length];
							/*[ENDIF]*/
							
							String.compressedArrayCopy(value, 0, newData, 0, start);
							String.compressedArrayCopy(value, end, newData, start + size, currentLength - end);
							
							value = newData;
							
							/*[IF Sidecar19-SE]*/
							shared = false;
							/*[ELSE]*/
							capacity = capacity & ~sharedBit;
							/*[ENDIF]*/
						}
					} else if (difference < 0) {
						move(-difference, end);
					/*[IF Sidecar19-SE]*/
					} else if (shared) {
					/*[ELSE]*/
					} else if (capacity < 0) {
					/*[ENDIF]*/
						value = value.clone();
						
						/*[IF Sidecar19-SE]*/
						shared = false;
						/*[ELSE]*/
						capacity = capacity & ~sharedBit;
						/*[ENDIF]*/
					}
					
					string.getBytes(0, size, value, start);
					
					count = currentLength - difference;
					
					return this;
				}
				
				if (start == end) {
					return insert(start, string);
				}
			}
		} else {
			// Check if the StringBuffer is compressed
			if (count >= 0) {
				decompress(value.length);				
			}
			
			if (start >= 0) {
				if (end > currentLength) {
					end = currentLength;
				}
				
				if (end > start) {
					int size = string.lengthInternal();
					
					// Difference between the substring we wish to replace and the size of the string parameter
					int difference = end - start - size;
					
					if (difference > 0) {
						// Check if the StringBuffer is not shared
						/*[IF Sidecar19-SE]*/
						if (!shared) {
							String.decompressedArrayCopy(value, end, value, start + size, currentLength - end);
						/*[ELSE]*/
						if (capacity >= 0) {
							System.arraycopy(value, end, value, start + size, currentLength - end);
						/*[ENDIF]*/
						} else {
							/*[IF Sidecar19-SE]*/
							byte[] newData = new byte[value.length];
							
							String.decompressedArrayCopy(value, 0, newData, 0, start);
							String.decompressedArrayCopy(value, end, newData, start + size, currentLength - end);
							/*[ELSE]*/
							char[] newData = new char[value.length];
							
							System.arraycopy(value, 0, newData, 0, start);
							System.arraycopy(value, end, newData, start + size, currentLength - end);
							/*[ENDIF]*/
							
							
							value = newData;
							
							/*[IF Sidecar19-SE]*/
							shared = false;
							/*[ELSE]*/
							capacity = capacity & ~sharedBit;
							/*[ENDIF]*/
						}
					} else if (difference < 0) {
						move(-difference, end);
					/*[IF Sidecar19-SE]*/
					} else if (shared) {
					/*[ELSE]*/
					} else if (capacity < 0) {
					/*[ENDIF]*/
						value = value.clone();
						
						/*[IF Sidecar19-SE]*/
						shared = false;
						/*[ELSE]*/
						capacity = capacity & ~sharedBit;
						/*[ENDIF]*/
					}
					
					string.getChars(0, size, value, start);
					
					count = (currentLength - difference) | uncompressedBit;
					
					return this;
				}
				
				if (start == end) {
					string.getClass(); // Implicit null check
					
					return insert(start, string);
				}
			}
		}
	} else {
		if (start >= 0) {
			if (end > currentLength) {
				end = currentLength;
			}
			
			if (end > start) {
				int size = string.lengthInternal();
				
				// Difference between the substring we wish to replace and the size of the string parameter
				int difference = end - start - size;
				
				if (difference > 0) {
					// Check if the StringBuffer is not shared
					/*[IF Sidecar19-SE]*/
					if (!shared) {
						String.decompressedArrayCopy(value, end, value, start + size, currentLength - end);
					/*[ELSE]*/
					if (capacity >= 0) {
						System.arraycopy(value, end, value, start + size, currentLength - end);
					/*[ENDIF]*/
					} else {
						/*[IF Sidecar19-SE]*/
						byte[] newData = new byte[value.length];
						
						String.decompressedArrayCopy(value, 0, newData, 0, start);
						String.decompressedArrayCopy(value, end, newData, start + size, currentLength - end);
						/*[ELSE]*/
						char[] newData = new char[value.length];
						
						System.arraycopy(value, 0, newData, 0, start);
						System.arraycopy(value, end, newData, start + size, currentLength - end);
						/*[ENDIF]*/
						
						value = newData;
						
						/*[IF Sidecar19-SE]*/
						shared = false;
						/*[ELSE]*/
						capacity = capacity & ~sharedBit;
						/*[ENDIF]*/
					}
				} else if (difference < 0) {
					move(-difference, end);
				/*[IF Sidecar19-SE]*/
				} else if (shared) {
				/*[ELSE]*/
				} else if (capacity < 0) {
				/*[ENDIF]*/
					value = value.clone();
					
					/*[IF Sidecar19-SE]*/
					shared = false;
					/*[ELSE]*/
					capacity = capacity & ~sharedBit;
					/*[ENDIF]*/
				}
				
				string.getChars(0, size, value, start);
				
				count = currentLength - difference;
				
				return this;
			}
			
			if (start == end) {
				string.getClass(); // Implicit null check
				
				return insert(start, string);
			}
		}
	}
	
	throw new StringIndexOutOfBoundsException();
}

/**
 * Reverses the order of characters in this StringBuffer.
 *
 * @return		this StringBuffer
 */
public synchronized StringBuffer reverse() {
	int currentLength = lengthInternalUnsynchronized();
	
	if (currentLength < 2) {
		return this;
	}
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		// Check if the StringBuffer is not shared
		/*[IF Sidecar19-SE]*/
		if (!shared) {
		/*[ELSE]*/
		if (capacity >= 0) {
		/*[ENDIF]*/
			for (int i = 0, mid = currentLength / 2, j = currentLength - 1; i < mid; ++i, --j) {
				byte a = helpers.getByteFromArrayByIndex(value, i);
				byte b = helpers.getByteFromArrayByIndex(value, j);
				
				helpers.putByteInArrayByIndex(value, i, b);
				helpers.putByteInArrayByIndex(value, j, a);
			}
			
			return this;
		} else {
			/*[IF Sidecar19-SE]*/
			byte[] newData = new byte[value.length];
			/*[ELSE]*/
			char[] newData = new char[value.length];
			/*[ENDIF]*/
			
			for (int i = 0, j = currentLength - 1; i < currentLength; ++i, --j) {
				helpers.putByteInArrayByIndex(newData, j, helpers.getByteFromArrayByIndex(value, i));
			}
			
			value = newData;
			
			/*[IF Sidecar19-SE]*/
			shared = false;
			/*[ELSE]*/
			capacity = capacity & ~sharedBit;
			/*[ENDIF]*/
			
			return this;
		}
	} else {
		// Check if the StringBuffer is not shared
		/*[IF Sidecar19-SE]*/
		if (!shared) {
		/*[ELSE]*/
		if (capacity >= 0) {
		/*[ENDIF]*/
			int end = currentLength - 1;
			
			/*[IF Sidecar19-SE]*/
			char frontHigh = helpers.getCharFromArrayByIndex(value, 0);
			char endLow = helpers.getCharFromArrayByIndex(value, end);
			/*[ELSE]*/
			char frontHigh = value[0];
			char endLow = value[end];
			/*[ENDIF]*/
			boolean allowFrontSur = true, allowEndSur = true;
			for (int i = 0, mid = currentLength / 2; i < mid; i++, --end) {
				/*[IF Sidecar19-SE]*/
				char frontLow = helpers.getCharFromArrayByIndex(value, i + 1);
				char endHigh = helpers.getCharFromArrayByIndex(value, end - 1);
				/*[ELSE]*/
				char frontLow = value[i + 1];
				char endHigh = value[end - 1];
				/*[ENDIF]*/
				boolean surAtFront = false, surAtEnd = false;
				if (allowFrontSur && frontLow >= Character.MIN_LOW_SURROGATE && frontLow <= Character.MAX_LOW_SURROGATE && frontHigh >= Character.MIN_HIGH_SURROGATE && frontHigh <= Character.MAX_HIGH_SURROGATE) {
					surAtFront = true;
					/*[PR 117344, CMVC 93149] ArrayIndexOutOfBoundsException in StringBuffer.reverse() */
					if (currentLength < 3) return this;
				}
				if (allowEndSur && endHigh >= Character.MIN_HIGH_SURROGATE && endHigh <= Character.MAX_HIGH_SURROGATE && endLow >= Character.MIN_LOW_SURROGATE && endLow <= Character.MAX_LOW_SURROGATE) {
					surAtEnd = true;
				}
				allowFrontSur = true;
				allowEndSur = true;
				if (surAtFront == surAtEnd) {
					if (surAtFront) {
						// both surrogates
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, end, frontLow);
						helpers.putCharInArrayByIndex(value, end - 1, frontHigh);
						helpers.putCharInArrayByIndex(value, i, endHigh);
						helpers.putCharInArrayByIndex(value, i + 1, endLow);
						frontHigh = helpers.getCharFromArrayByIndex(value, i + 2);
						endLow = helpers.getCharFromArrayByIndex(value, end - 2);
						/*[ELSE]*/
						value[end] = frontLow;
						value[end - 1] = frontHigh;
						value[i] = endHigh;
						value[i + 1] = endLow;
						frontHigh = value[i + 2];
						endLow = value[end - 2];
						/*[ENDIF]*/
						i++;
						--end;
					} else {
						// neither surrogates
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, end, frontHigh);
						helpers.putCharInArrayByIndex(value, i, endLow);
						/*[ELSE]*/
						value[end] = frontHigh;
						value[i] = endLow;
						/*[ENDIF]*/
						frontHigh = frontLow;
						endLow = endHigh;
					}
				} else {
					if (surAtFront) {
						// surrogate only at the front
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, end, frontLow);
						helpers.putCharInArrayByIndex(value, i, endLow);
						/*[ELSE]*/
						value[end] = frontLow;
						value[i] = endLow;
						/*[ENDIF]*/
						endLow = endHigh;
						allowFrontSur = false;
					} else {
						// surrogate only at the end
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, end, frontHigh);
						helpers.putCharInArrayByIndex(value, i, endHigh);
						/*[ELSE]*/
						value[end] = frontHigh;
						value[i] = endHigh;
						/*[ENDIF]*/
						frontHigh = frontLow;
						allowEndSur = false;
					}
				}
			}
			if ((currentLength & 1) == 1 && (!allowFrontSur || !allowEndSur)) {
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(value, end, allowFrontSur ? endLow : frontHigh);
				/*[ELSE]*/
				value[end] = allowFrontSur ? endLow : frontHigh;
				/*[ENDIF]*/
			}
		} else {
			/*[IF Sidecar19-SE]*/
			byte[] newData = new byte[value.length];
			/*[ELSE]*/
			char[] newData = new char[value.length];
			/*[ENDIF]*/
			
			for (int i = 0, end = currentLength; i < currentLength; i++) {
				/*[IF Sidecar19-SE]*/
				char high = helpers.getCharFromArrayByIndex(value, i);
				/*[ELSE]*/
				char high = value[i];
				/*[ENDIF]*/

				if ((i + 1) < currentLength && high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
					/*[IF Sidecar19-SE]*/
					char low = helpers.getCharFromArrayByIndex(value, i + 1);
					/*[ELSE]*/
					char low = value[i + 1];
					/*[ENDIF]*/
					
					if (low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(newData, --end, low);
						/*[ELSE]*/
						newData[--end] = low;
						/*[ENDIF]*/
						i++;
					}
				}
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(newData, --end, high);
				/*[ELSE]*/
				newData[--end] = high;
				/*[ENDIF]*/
			}
			
			value = newData;
			
			/*[IF Sidecar19-SE]*/
			shared = false;
			/*[ELSE]*/
			capacity = capacity & ~sharedBit;
			/*[ENDIF]*/
		}
		
		return this;
	}
}

/**
 * Sets the character at the specified offset in this StringBuffer.
 *
 * @param 		index	the zero-based index in this StringBuffer
 * @param		ch	the character
 *
 * @exception	IndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index >= length()</code>
 */
public synchronized void setCharAt(int index, char ch) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (0 <= index && index < currentLength) {
		if (String.enableCompression) {
			// Check if the StringBuffer is compressed
			if (count >= 0 && ch <= 255) {
				/*[IF Sidecar19-SE]*/
				if (shared) {
				/*[ELSE]*/
				if (capacity < 0) {
				/*[ENDIF]*/
					value = value.clone();
					
					/*[IF Sidecar19-SE]*/
					shared = false;
					/*[ELSE]*/
					capacity = capacity & ~sharedBit;
					/*[ENDIF]*/
				}
				
				helpers.putByteInArrayByIndex(value, index, (byte) ch);
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(value.length);
				}

				/*[IF Sidecar19-SE]*/
				if (shared) {
				/*[ELSE]*/
				if (capacity < 0) {
				/*[ENDIF]*/
					value = value.clone();
					
					/*[IF Sidecar19-SE]*/
					shared = false;
					/*[ELSE]*/
					capacity = capacity & ~sharedBit;
					/*[ENDIF]*/
				}
				
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(value, index, (char) ch);
				/*[ELSE]*/
				value[index] = ch;
				/*[ENDIF]*/
			}
		} else {
			/*[IF Sidecar19-SE]*/
			if (shared) {
			/*[ELSE]*/
			if (capacity < 0) {
			/*[ENDIF]*/
				value = value.clone();
				
				/*[IF Sidecar19-SE]*/
				shared = false;
				/*[ELSE]*/
				capacity = capacity & ~sharedBit;
				/*[ENDIF]*/
			}

			/*[IF Sidecar19-SE]*/
			helpers.putCharInArrayByIndex(value, index, (char) ch);
			/*[ELSE]*/
			value[index] = ch;
			/*[ENDIF]*/
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Sets the length of this StringBuffer to the specified length. If there
 * are more than length characters in this StringBuffer, the characters
 * at end are lost. If there are less than length characters in the
 * StringBuffer, the additional characters are set to <code>\\u0000</code>.
 *
 * @param		length	the new length of this StringBuffer
 *
 * @exception	IndexOutOfBoundsException when <code>length < 0</code>
 *
 * @see			#length
 */
public synchronized void setLength(int length) {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		if (length > currentCapacity) {
			ensureCapacityImpl(length);
		} else if (length > currentLength) {
			/*[IF Sidecar19-SE]*/
			Arrays.fill(value, currentLength, length, (byte) 0);
		} else if (shared) {
			/*[ELSE]*/
			for (int i = currentLength; i < length; ++i) {
				helpers.putByteInArrayByIndex(value, i, (byte) 0);
			}
		} else if (capacity < 0) {
			/*[ENDIF]*/
			if (length < 0) {
				throw new IndexOutOfBoundsException();
			}
			
			/*[IF Sidecar19-SE]*/
			byte[] newData = new byte[value.length];
			/*[ELSE]*/
			char[] newData = new char[value.length];
			/*[ENDIF]*/
			
			if (length > 0) {
				String.compressedArrayCopy(value, 0, newData, 0, length);
			}
			
			value = newData;
			
			/*[IF Sidecar19-SE]*/
			shared = false;
			/*[ELSE]*/
			capacity = capacity & ~sharedBit;
			/*[ENDIF]*/
		} else if (length < 0) {
			throw new IndexOutOfBoundsException();
		}
	} else {
		if (length > currentCapacity) {
			ensureCapacityImpl(length);
		} else if (length > currentLength) {
			/*[PR CMVC 104709] Zero characters when growing */
			/*[IF Sidecar19-SE]*/
			Arrays.fill(value, currentLength * 2, length * 2, (byte) 0);
		} else if (shared) {
			/*[ELSE]*/
			Arrays.fill(value, currentLength, length, (char) 0);
		} else if (capacity < 0) {
			/*[ENDIF]*/
			if (length < 0) {
				throw new IndexOutOfBoundsException();
			}
			
			/*[PR 109954] Do not reduce capacity */
			/*[IF Sidecar19-SE]*/
			byte[] newData = new byte[value.length];
			/*[ELSE]*/
			char[] newData = new char[value.length];
			/*[ENDIF]*/
			
			if (length > 0) {
				/*[IF Sidecar19-SE]*/
				String.decompressedArrayCopy(value, 0, newData, 0, length);
				/*[ELSE]*/
				System.arraycopy(value, 0, newData, 0, length);
				/*[ENDIF]*/
			}
			
			value = newData;
			
			/*[IF Sidecar19-SE]*/
			shared = false;
			/*[ELSE]*/
			capacity = capacity & ~sharedBit;
			/*[ENDIF]*/
		} else if (length < 0) {
			throw new IndexOutOfBoundsException();
		}
	}
	
	if (String.enableCompression) {
		// Check if the StringBuffer is compressed
		if (count >= 0) {
			count = length;
		} else {
			count = length | uncompressedBit;
		}
	} else {
		count = length;
	}
}

/**
 * Copies a range of characters into a new String.
 *
 * @param		start	the offset of the first character
 * @return		a new String containing the characters from start to the end
 *				of the string
 *
 * @exception	StringIndexOutOfBoundsException when <code>start < 0</code> or
 *				<code>start > length()</code>
 */
public synchronized String substring(int start) {
	int currentLength = lengthInternalUnsynchronized();

	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		if (0 <= start && start <= currentLength) {
			/*[PR CMVC 104709] Remove String sharing for more performance */
			return new String(value, start, currentLength - start, true, false);
		}
	} else {
		if (0 <= start && start <= currentLength) {
			/*[PR CMVC 104709] Remove String sharing for more performance */
			return new String(value, start, currentLength - start, false, false);
		}
	}
	
	throw new StringIndexOutOfBoundsException(start);
}

/**
 * Copies a range of characters into a new String.
 *
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @return		a new String containing the characters from start to end - 1
 *
 * @exception	StringIndexOutOfBoundsException when <code>start < 0, start > end</code> or
 *				<code>end > length()</code>
 */
public synchronized String substring(int start, int end) {
	int currentLength = lengthInternalUnsynchronized();
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		if (0 <= start && start <= end && end <= currentLength) {
			/*[PR CMVC 104709] Remove String sharing for more performance */
			return new String(value, start, end - start, true, false);
		}
	} else {
		if (0 <= start && start <= end && end <= currentLength) {
			/*[PR CMVC 104709] Remove String sharing for more performance */
			return new String(value, start, end - start, false, false);
		}
	}
	
	throw new StringIndexOutOfBoundsException();
}

/*[IF]*/
// TODO : This is no longer applicable because String does not have an offset field.
/*[ENDIF]*/
static void initFromSystemProperties(Properties props) {
	String prop = props.getProperty("java.lang.string.create.unique"); //$NON-NLS-1$
	TOSTRING_COPY_BUFFER_ENABLED = "true".equals(prop) || "StringBuffer".equals(prop); //$NON-NLS-1$ //$NON-NLS-2$
	/*[IF]*/
	if (TOSTRING_COPY_BUFFER_ENABLED) {
		com.ibm.oti.vm.VM.dumpString("PMR 67389 - Creating unique String.char[]s from java.lang.StringBuffer.toString(). Disable using -Djava.lang.string.create.unique=false\n"); //$NON-NLS-1$
	}
	/*[ENDIF]*/
}


/**
 * Answers the contents of this StringBuffer.
 *
 * @return		a String containing the characters in this StringBuffer
 */
public synchronized String toString () {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	if (false == TOSTRING_COPY_BUFFER_ENABLED) {
		/*[PR 96029] Copy char[] when too much memory wasted */
		/*[PR CMVC 104709] Optimize String sharing for more performance */
		int wasted = currentCapacity - currentLength;
		/*[PR CVMC 106450] Fix CaffeineMark StringAtom benchmark */
		if (wasted >= 768 || (wasted >= INITIAL_SIZE && wasted >= (currentCapacity >> 1))) {
			// Check if the StringBuffer is compressed
			if (String.enableCompression && count >= 0) {
				return new String (value, 0, currentLength, true, false);
			} else {
				return new String (value, 0, currentLength, false, false);
			}
		}
	} else {
		// Do not copy the char[] if it will not get smaller because of object alignment
		int roundedCount = (currentLength + 3) & ~3;
		if (roundedCount < currentCapacity) {
			// Check if the StringBuffer is compressed
			if (String.enableCompression && count >= 0) {
				return new String (value, 0, currentLength, true, false);
			} else {
				return new String (value, 0, currentLength, false, false);
			}
		}
	}
	
	/*[IF Sidecar19-SE]*/
	shared = true;
	/*[ELSE]*/
	capacity = capacity | sharedBit;
	/*[ENDIF]*/
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		return new String (value, 0, currentLength, true);
	} else {
		return new String (value, 0, currentLength, false);
	}
}

private synchronized void writeObject(ObjectOutputStream stream) throws IOException {
	int currentLength = lengthInternalUnsynchronized();
	
	ObjectOutputStream.PutField pf = stream.putFields();
	
	pf.put("count", currentLength); //$NON-NLS-1$  

	/*[IF Sidecar19-SE]*/
	char[] newData = new char[currentLength];
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		String.decompress(value, 0, newData, 0, currentLength);
	} else {
		String.decompressedArrayCopy(value, 0, newData, 0, currentLength);
	}

	pf.put("value", newData); //$NON-NLS-1$
	/*[ELSE]*/
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		char[] newData = new char[currentLength];
		
		String.decompress(value, 0, newData, 0, currentLength);
		
		pf.put("value", newData); //$NON-NLS-1$
	} else {
		pf.put("value", value); //$NON-NLS-1$
	}
	/*[ENDIF]*/
	
	pf.put("shared", false); //$NON-NLS-1$
	
	stream.writeFields();
}

private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
	ObjectInputStream.GetField gf = stream.readFields();
	
	int streamCount = gf.get("count", 0); //$NON-NLS-1$

	char[] streamValue = (char[]) gf.get("value", null); //$NON-NLS-1$
	
	if (streamCount > streamValue.length) {
		throw new InvalidObjectException(com.ibm.oti.util.Msg.getString("K0199")); //$NON-NLS-1$
	} 
	
	if (String.enableCompression) {
		if (String.compressible(streamValue, 0, streamValue.length)) {
			/*[IF Sidecar19-SE]*/
			value = new byte[streamValue.length];
			/*[ELSE]*/
			if (streamValue.length == Integer.MAX_VALUE) {
				value = new char[(streamValue.length / 2) + 1];
			} else {
				value = new char[(streamValue.length + 1) / 2];
			}
			/*[ENDIF]*/
			
			String.compress(streamValue, 0, value, 0, streamValue.length);
			
			count = streamCount;
			
			/*[IF !Sidecar19-SE]*/
			capacity = streamValue.length;
			/*[ENDIF]*/
		} else {
			/*[IF Sidecar19-SE]*/
			value = new byte[streamValue.length * 2];
			
			String.decompressedArrayCopy(streamValue, 0, value, 0, streamValue.length);
			/*[ELSE]*/
			value = new char[streamValue.length];
			
			System.arraycopy(streamValue, 0, value, 0, streamValue.length);
			/*[ENDIF]*/
			
			count = streamCount | uncompressedBit;

			/*[IF !Sidecar19-SE]*/
			capacity = streamValue.length;
			/*[ENDIF]*/
			
			String.initCompressionFlag();
		}
	} else {
		/*[IF Sidecar19-SE]*/
		value = new byte[streamValue.length * 2];

		String.decompressedArrayCopy(streamValue, 0, value, 0, streamValue.length);
		/*[ELSE]*/
		value = new char[streamValue.length];
		
		System.arraycopy(streamValue, 0, value, 0, streamValue.length);
		/*[ENDIF]*/
		
		count = streamCount;
		
		/*[IF !Sidecar19-SE]*/
		capacity = streamValue.length;
		/*[ENDIF]*/
	}
}

/**
 * Adds the specified StringBuffer to the end of this StringBuffer.
 *
 * @param		buffer	the StringBuffer
 * @return		this StringBuffer
 * 
 * @since 1.4
 */
public synchronized StringBuffer append(StringBuffer buffer) {
	if (buffer == null) {
		return append((String)null);
	} else {
		synchronized (buffer) {
			// Check if the StringBuffer is compressed
			if (String.enableCompression && buffer.count >= 0) {
				return append(buffer.value, 0, buffer.lengthInternalUnsynchronized(), true);
			} else {
				return append(buffer.value, 0, buffer.lengthInternalUnsynchronized(), false);
			}
		}
	}
}

/**
 * Copies a range of characters into a new String.
 *
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @return		a new String containing the characters from start to end - 1
 *
 * @exception	IndexOutOfBoundsException when <code>start < 0, start > end</code> or
 *				<code>end > length()</code>
 * 
 * @since 1.4
 */
public CharSequence subSequence(int start, int end) {
	return substring(start, end);
}

/**
 * Searches in this StringBuffer for the first index of the specified character. The
 * search for the character starts at the beginning and moves towards the
 * end.
 *
 * @param		string	the string to find
 * @return		the index in this StringBuffer of the specified character, -1 if the
 *				character isn't found
 *
 * @see			#lastIndexOf(String)
 * 
 * @since 1.4
 */
public int indexOf(String string) {
	return indexOf(string, 0);
}

/**
 * Searches in this StringBuffer for the index of the specified character. The
 * search for the character starts at the specified offset and moves towards
 * the end.
 *
 * @param		subString		the string to find
 * @param		start	the starting offset
 * @return		the index in this StringBuffer of the specified character, -1 if the
 *				character isn't found
 *
 * @see			#lastIndexOf(String,int)
 * 
 * @since 1.4
 */
public synchronized int indexOf(String subString, int start) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (start < 0) {
		start = 0;
	}
	
	int subStringLength = subString.lengthInternal();
	
	if (subStringLength > 0) {
		if (subStringLength + start > currentLength) {
			return -1;
		}
		
		char firstChar = subString.charAtInternal(0);
		
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			if (!subString.isCompressed()) {
				return -1;
			}
			
			while (true) {
				int i = start;
				
				boolean found = false;
				
				for (; i < currentLength; ++i) {
					if (helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, i)) == firstChar) {
						found = true;
						break;
					}
				}
				
				// Handles subStringLength > currentLength || start >= currentLength
				if (!found || subStringLength + i > currentLength) {
					return -1; 
				}
				
				int o1 = i;
				int o2 = 0;
				
				while (++o2 < subStringLength && helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, ++o1)) == subString.charAtInternal(o2));
				
				if (o2 == subStringLength) {
					return i;
				}
				
				start = i + 1;
			}
		} else {
			while (true) {
				int i = start;
				
				boolean found = false;
				
				for (; i < currentLength; ++i) {
					/*[IF Sidecar19-SE]*/
					if (helpers.getCharFromArrayByIndex(value, i) == firstChar) {
					/*[ELSE]*/
					if (value[i] == firstChar) {
					/*[ENDIF]*/
						found = true;
						break;
					}
				}
				
				// Handles subStringLength > currentLength || start >= currentLength
				if (!found || subStringLength + i > currentLength) {
					return -1; 
				}
				
				int o1 = i;
				int o2 = 0;
				
				/*[IF Sidecar19-SE]*/
				while (++o2 < subStringLength && helpers.getCharFromArrayByIndex(value, ++o1) == subString.charAtInternal(o2));
				/*[ELSE]*/
				while (++o2 < subStringLength && value[++o1] == subString.charAtInternal(o2));
				/*[ENDIF]*/
				
				if (o2 == subStringLength) {
					return i;
				}
				
				start = i + 1;
			}
		}
		
	} else {
		return (start < currentLength || start == 0) ? start : currentLength;
	}
}

/**
 * Searches in this StringBuffer for the last index of the specified character. The
 * search for the character starts at the end and moves towards the beginning.
 *
 * @param		string	the string to find
 * @return		the index in this StringBuffer of the specified character, -1 if the
 *				character isn't found
 *
 * @see			#indexOf(String)
 * 
 * @since 1.4
 */
public synchronized int lastIndexOf(String string) {
	int currentLength = lengthInternalUnsynchronized();
	
	return lastIndexOf(string, currentLength);
}

/**
 * Searches in this StringBuffer for the index of the specified character. The
 * search for the character starts at the specified offset and moves towards
 * the beginning.
 *
 * @param		subString		the string to find
 * @param		start	the starting offset
 * @return		the index in this StringBuffer of the specified character, -1 if the
 *				character isn't found
 *
 * @see			#indexOf(String,int)
 * 
 * @since 1.4
 */
public synchronized int lastIndexOf(String subString, int start) {
	int currentLength = lengthInternalUnsynchronized();
	
	int subStringLength = subString.lengthInternal();
	
	if (subStringLength <= currentLength && start >= 0) {
		if (subStringLength > 0) {
			if (start > currentLength - subStringLength) {
				start = currentLength - subStringLength;
			}
			
			char firstChar = subString.charAtInternal(0);
			
			// Check if the StringBuffer is compressed
			if (String.enableCompression && count >= 0) {
				if (!subString.isCompressed()) {
					return -1;
				}
				
				while (true) {
					int i = start;
					
					boolean found = false;
					
					for (; i >= 0; --i) {
						if (helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, i)) == firstChar) {
							found = true;
							break;
						}
					}
					
					if (!found) {
						return -1;
					}
					
					int o1 = i;
					int o2 = 0;
					
					while (++o2 < subStringLength && helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, ++o1)) == subString.charAtInternal(o2));
					
					if (o2 == subStringLength) {
						return i;
					}
					
					start = i - 1;
				}
			} else {
				while (true) {
					int i = start;
					
					boolean found = false;
					
					for (; i >= 0; --i) {
						/*[IF Sidecar19-SE]*/
						if (helpers.getCharFromArrayByIndex(value, i) == firstChar) {
						/*[ELSE]*/
						if (value[i] == firstChar) {
						/*[ENDIF]*/
							found = true;
							break;
						}
					}
					
					if (!found) {
						return -1;
					}
					
					int o1 = i;
					int o2 = 0;
					
					/*[IF Sidecar19-SE]*/
					while (++o2 < subStringLength && helpers.getCharFromArrayByIndex(value, ++o1) == subString.charAtInternal(o2));
					/*[ELSE]*/
					while (++o2 < subStringLength && value[++o1] == subString.charAtInternal(o2));
					/*[ENDIF]*/
					
					if (o2 == subStringLength) {
						return i;
					}
					
					start = i - 1;
				}
			}
		} else {
			return start < currentLength ? start : currentLength;
		}
	} else {
		return -1;
	}
}

/*
 * Return the underlying buffer and set the shared flag.
 *
 */
/*[IF Sidecar19-SE]*/
byte[] shareValue() {
	shared = true;
/*[ELSE]*/
char[] shareValue() {
	capacity = capacity | sharedBit;
/*[ENDIF]*/
	
	return value;
}

boolean isCompressed() {
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		return true;
	} else {
		return false;
	}
}

/**
 * Constructs a new StringBuffer containing the characters in
 * the specified CharSequence and the default capacity.
 *
 * @param		sequence	the initial contents of this StringBuffer
 * @exception	NullPointerException when sequence is null
 * 
 * @since 1.5
 */
public StringBuffer(CharSequence sequence) {
	int size = sequence.length();
	
	if (size < 0) {
		size = 0;
	}
	
	int newLength = INITIAL_SIZE + size;
	
	if (String.enableCompression) {
		/*[IF Sidecar19-SE]*/
		value = new byte[newLength];
		/*[ELSE]*/
		value = new char[(newLength + 1) / 2];
		/*[ENDIF]*/
	} else {
		/*[IF Sidecar19-SE]*/
		value = new byte[newLength * 2];
		/*[ELSE]*/
		value = new char[newLength];
		/*[ENDIF]*/
	}

	/*[IF !Sidecar19-SE]*/
	capacity = newLength;
	/*[ENDIF]*/
	
	if (sequence instanceof String) {
		append((String)sequence);
	} else if (sequence instanceof StringBuffer) {		
		append((StringBuffer)sequence);
	} else {		
		if (String.enableCompression) {
			boolean isCompressed = true;
			
			for (int i = 0; i < size; ++i) {
				if (sequence.charAt(i) > 255) {
					isCompressed = false;
					
					break;
				}
			}
			
			if (isCompressed) {				
				count = size;
				
				for (int i = 0; i < size; ++i) {
					helpers.putByteInArrayByIndex(value, i, (byte) sequence.charAt(i));
				}
			} else {
				/*[IF Sidecar19-SE]*/
				value = new byte[newLength * 2];
				/*[ELSE]*/
				value = new char[newLength];
				/*[ENDIF]*/
				
				count = size | uncompressedBit;
				
				for (int i = 0; i < size; ++i) {
					/*[IF Sidecar19-SE]*/
					helpers.putCharInArrayByIndex(value, i, (char) sequence.charAt(i));
					/*[ELSE]*/
					value[i] = sequence.charAt(i);
					/*[ENDIF]*/
				}
				
				String.initCompressionFlag();
			}
		} else {			
			count = size;
			
			for (int i = 0; i < size; ++i) {
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(value, i, (char) sequence.charAt(i));
				/*[ELSE]*/
				value[i] = sequence.charAt(i);
				/*[ENDIF]*/
			}
		}
	}
}

/**
 * Adds the specified CharSequence to the end of this StringBuffer.
 *
 * @param		sequence	the CharSequence
 * @return		this StringBuffer
 * 
 * @since 1.5
 */
public synchronized StringBuffer append(CharSequence sequence) {
	if (sequence == null) {
		return append(String.valueOf(sequence));
	} else if (sequence instanceof String) {
		return append((String)sequence);
	} else if (sequence instanceof StringBuffer) {
		return append((StringBuffer)sequence);
	} else {
		int currentLength = lengthInternalUnsynchronized();
		int currentCapacity = capacityInternal();
		
		int sequenceLength = sequence.length();
		
		int newLength = currentLength + sequenceLength;
		
		if (String.enableCompression) {
			boolean isCompressed = true;
			
			if (count >= 0) {
				for (int i = 0; i < sequence.length(); ++i) {
					if (sequence.charAt(i) > 255) {
						isCompressed = false;
						
						break;
					}
				}
			}
			
			// Check if the StringBuffer is compressed
			if (count >= 0 && isCompressed) {
				if (newLength > currentCapacity) {
					ensureCapacityImpl(newLength);
				}
				
				for (int i = 0; i < sequence.length(); ++i) {
					helpers.putByteInArrayByIndex(value, currentLength + i, (byte) sequence.charAt(i));
				}
				
				count = newLength;
			} else {
				// Check if the StringBuffer is compressed
				if (count >= 0) {
					decompress(newLength);
				}
				
				if (newLength > currentCapacity) {
					ensureCapacityImpl(newLength);
				}
				
				for (int i = 0; i < sequence.length(); ++i) {
					/*[IF Sidecar19-SE]*/
					helpers.putCharInArrayByIndex(value, currentLength + i, (char) sequence.charAt(i));
					/*[ELSE]*/
					value[currentLength + i] = sequence.charAt(i);
					/*[ENDIF]*/
				}
				
				count = newLength | uncompressedBit;
			}
		} else {
			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}
			
			for (int i = 0; i < sequence.length(); ++i) {
				/*[IF Sidecar19-SE]*/
				helpers.putCharInArrayByIndex(value, currentLength + i, (char) sequence.charAt(i));
				/*[ELSE]*/
				value[currentLength + i] = sequence.charAt(i);
				/*[ENDIF]*/
			}
			
			count = newLength;
		}
		
		return this;
	}
}

/**
 * Adds the specified CharSequence to the end of this StringBuffer.
 *
 * @param		sequence	the CharSequence
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @return		this StringBuffer
 * 
 * @exception	IndexOutOfBoundsException when <code>start < 0, start > end</code> or
 *				<code>end > length()</code>
 * 
 * @since 1.5
 */
public synchronized StringBuffer append(CharSequence sequence, int start, int end) {
	if (sequence == null) {
		return append(String.valueOf(sequence), start, end);
	} else if (sequence instanceof String) {
		return append(((String)sequence).substring(start, end));
	} else if (start >= 0 && end >= 0 && start <= end && end <= sequence.length()) {
		if (sequence instanceof StringBuffer) {
			synchronized (sequence) {
				StringBuffer buffer = (StringBuffer) sequence;
				
				// Check if the StringBuffer is compressed
				if (String.enableCompression && buffer.count >= 0) {
					return append(buffer.value, start, end - start, true);
				} else {
					return append(buffer.value, start, end - start, false);
				}
			}
		} else if (sequence instanceof StringBuilder) {
			synchronized (sequence) {
				StringBuilder builder = (StringBuilder) sequence;
				
				if (String.enableCompression && builder.isCompressed()) {
					return append(builder.getValue(), start, end - start, true);
				} else {
					return append(builder.getValue(), start, end - start, false);
				}
			}
		} else {
			int currentLength = lengthInternalUnsynchronized();
			int currentCapacity = capacityInternal();
			
			int newLength = currentLength + end - start;
			
			if (String.enableCompression) {
				boolean isCompressed = true;
				
				if (count >= 0) {
					for (int i = 0; i < sequence.length(); ++i) {
						if (sequence.charAt(i) > 255) {
							isCompressed = false;
							
							break;
						}
					}
				}
				
				// Check if the StringBuffer is compressed
				if (count >= 0 && isCompressed) {
					if (newLength > currentCapacity) {
						ensureCapacityImpl(newLength);
					}
					
					for (int i = 0; i < end - start; ++i) {
						helpers.putByteInArrayByIndex(value, currentLength + i, (byte) sequence.charAt(start + i));
					}
					
					count = newLength;
				} else {
					// Check if the StringBuffer is compressed
					if (count >= 0) {
						decompress(newLength);
					}
					
					if (newLength > currentCapacity) {
						ensureCapacityImpl(newLength);
					}
					
					for (int i = 0; i < end - start; ++i) {
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, currentLength + i, (char) sequence.charAt(start + i));
						/*[ELSE]*/
						value[currentLength + i] = sequence.charAt(start + i);
						/*[ENDIF]*/
					}
					
					count = newLength | uncompressedBit;
				}
			} else {
				if (newLength > currentCapacity) {
					ensureCapacityImpl(newLength);
				}
				
				for (int i = 0; i < end - start; ++i) {
					/*[IF Sidecar19-SE]*/
					helpers.putCharInArrayByIndex(value, currentLength + i, (char) sequence.charAt(start + i));
					/*[ELSE]*/
					value[currentLength + i] = sequence.charAt(start + i);
					/*[ENDIF]*/
				}
				
				count = newLength;
			}
			
			return this;
		}	
	} else {
		throw new IndexOutOfBoundsException();
	}
}

/**
 * Inserts the CharSequence at the specified offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		sequence	the CharSequence to insert
 * @return		this StringBuffer
 *
 * @exception	IndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>
 * 
 * @since 1.5
 */
public synchronized StringBuffer insert(int index, CharSequence sequence) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (index >= 0 && index <= currentLength) {
		if (sequence == null) {
			return insert(index, String.valueOf(sequence));
		} else if (sequence instanceof String) {
			return insert(index, (String) sequence);
		} else if (sequence instanceof StringBuffer) {
			synchronized(sequence) {
				StringBuffer buffer = (StringBuffer) sequence;
				
				// Check if the StringBuffer is compressed
				if (String.enableCompression && buffer.count >= 0) {
					return insert(index, buffer.value, 0, buffer.lengthInternalUnsynchronized(), true);
				} else {
					return insert(index, buffer.value, 0, buffer.lengthInternalUnsynchronized(), false);
				}
			}
		} else if (sequence instanceof StringBuilder) {
			synchronized (sequence) {
				StringBuilder builder = (StringBuilder) sequence;
				
				if (String.enableCompression && builder.isCompressed()) {
					return insert(index, builder.getValue(), 0, builder.lengthInternal(), true);
				} else {
					return insert(index, builder.getValue(), 0, builder.lengthInternal(), false);
				}
			}
		} else {
			int sequneceLength = sequence.length();
			
			if (sequneceLength > 0) {
				move(sequneceLength, index);
				
				int newLength = currentLength + sequneceLength;
				
				if (String.enableCompression) {
					/*[IF]*/
					// TODO : This is very suboptimal. CharSequence needs to be compressified and an isCompressed method needs to be added.
					/*[ENDIF]*/
					boolean isCompressed = true;
					
					for (int i = 0; i < sequneceLength; ++i) {
						if (sequence.charAt(i) > 255) {
							isCompressed = false;
							
							break;
						}
					}
					
					// Check if the StringBuffer is compressed
					if (count >= 0 && isCompressed) {
						for (int i = 0; i < sequneceLength; ++i) {
							helpers.putByteInArrayByIndex(value, index + i, (byte) sequence.charAt(i));
						}
						
						count = newLength;
						
						return this;
					} else {
						// Check if the StringBuffer is compressed
						if (count >= 0) {
							decompress(value.length);
						}
						
						for (int i = 0; i < sequneceLength; ++i) {
							/*[IF Sidecar19-SE]*/
							helpers.putCharInArrayByIndex(value, index + i, (char) sequence.charAt(i));
							/*[ELSE]*/
							value[index + i] = sequence.charAt(i);
							/*[ENDIF]*/
						}
						
						count = newLength | uncompressedBit;
					}
				} else {
					for (int i = 0; i < sequneceLength; ++i) {
						/*[IF Sidecar19-SE]*/
						helpers.putCharInArrayByIndex(value, index + i, (char) sequence.charAt(i));
						/*[ELSE]*/
						value[index + i] = sequence.charAt(i);
						/*[ENDIF]*/
					}
					
					count = newLength;
				}
			}
			
			return this;
		}
	} else {
		throw new IndexOutOfBoundsException();
	}
}

/**
 * Inserts the CharSequence at the specified offset in this StringBuffer.
 *
 * @param		index	the index at which to insert
 * @param		sequence	the CharSequence to insert
 * @param		start	the offset of the first character
 * @param		end	the offset one past the last character
 * @return		this StringBuffer
 *
 * @exception	IndexOutOfBoundsException when <code>index < 0</code> or
 *				<code>index > length()</code>, or when <code>start < 0, start > end</code> or
 *				<code>end > length()</code>
 * 
 * @since 1.5
 */
public synchronized StringBuffer insert(int index, CharSequence sequence, int start, int end) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (index >= 0 && index <= currentLength) {
		if (sequence == null)
			return insert(index, String.valueOf(sequence), start, end);
		if (sequence instanceof String) {
			return insert(index, ((String) sequence).substring(start, end));
		}
		if (start >= 0 && end >= 0 && start <= end && end <= sequence.length()) {
			if (sequence instanceof StringBuffer) {
				synchronized(sequence) {
					StringBuffer buffer = (StringBuffer) sequence;
					
					// Check if the StringBuffer is compressed
					if (String.enableCompression && buffer.count >= 0) {
						return insert(index, buffer.value, start, end - start, true);
					} else {
						return insert(index, buffer.value, start, end - start, false);
					}
				}
			} else if (sequence instanceof StringBuilder) {
				synchronized(sequence) {
					StringBuilder builder = (StringBuilder) sequence;
					
					if (String.enableCompression && builder.isCompressed()) {
						return insert(index, builder.getValue(), start, end - start, true);
					} else {
						return insert(index, builder.getValue(), start, end - start, false);
					}
				}
			} else {
				int sequenceLength = end - start;
				
				if (sequenceLength > 0) {
					move(sequenceLength, index);
					
					int newLength = currentLength + sequenceLength;
					
					if (String.enableCompression) {
						/*[IF]*/
						// TODO : This is very suboptimal. CharSequence needs to be compressified and an isCompressed method needs to be added.
						/*[ENDIF]*/
						boolean isCompressed = true;
						
						for (int i = 0; i < sequenceLength; ++i) {
							if (sequence.charAt(start + i) > 255) {
								isCompressed = false;
								
								break;
							}
						}
						
						// Check if the StringBuffer is compressed
						if (count >= 0 && isCompressed) {
							for (int i = 0; i < sequenceLength; ++i) {
								helpers.putByteInArrayByIndex(value, index + i, (byte) sequence.charAt(start + i));
							}
							
							count = newLength;
							
							return this;
						} else {
							// Check if the StringBuffer is compressed
							if (count >= 0) {
								decompress(value.length);
							}
							
							for (int i = 0; i < sequenceLength; ++i) {
								/*[IF Sidecar19-SE]*/
								helpers.putCharInArrayByIndex(value, index + i, (char) sequence.charAt(start + i));
								/*[ELSE]*/
								value[index + i] = sequence.charAt(start + i);
								/*[ENDIF]*/
							}
							
							count = newLength | uncompressedBit;
						}
					} else {
						for (int i = 0; i < sequenceLength; ++i) {
							/*[IF Sidecar19-SE]*/
							helpers.putCharInArrayByIndex(value, index + i, (char) sequence.charAt(start + i));
							/*[ELSE]*/
							value[index + i] = sequence.charAt(start + i);
							/*[ENDIF]*/
						}
						
						count = newLength;
					}
				}
				
				return this;
			}
		} else {
			throw new IndexOutOfBoundsException();
		}
	} else {
		throw new IndexOutOfBoundsException();
	}
}

/**
 * Optionally modify the underlying char array to only
 * be large enough to hold the characters in this StringBuffer. 
 * 
 * @since		1.5
 */
public synchronized void trimToSize() {
	int currentLength = lengthInternalUnsynchronized();
	int currentCapacity = capacityInternal();
	
	// Check if the StringBuffer is compressed
	if (String.enableCompression && count >= 0) {
		// Check if the StringBuffer is not shared
		/*[IF Sidecar19-SE]*/
		if (!shared && currentCapacity != currentLength) {
			byte[] newData = new byte[currentLength];
		/*[ELSE]*/
		if (capacity >= 0 && currentCapacity != currentLength) {
			char[] newData = new char[(currentLength + 1) / 2];
		/*[ENDIF]*/
			
			String.compressedArrayCopy(value, 0, newData, 0, currentLength);
			
			value = newData;
			
			/*[IF !Sidecar19-SE]*/
			capacity = currentLength;
			/*[ENDIF]*/
		}
	} else {
		// Check if the StringBuffer is not shared
		/*[IF Sidecar19-SE]*/
		if (!shared && currentCapacity != currentLength) {
			byte[] newData = new byte[currentLength * 2];
			
			String.decompressedArrayCopy(value, 0, newData, 0, currentLength);
		/*[ELSE]*/
		if (capacity >= 0 && currentCapacity != currentLength) {
			char[] newData = new char[currentLength];
				
			System.arraycopy(value, 0, newData, 0, currentLength);
		/*[ENDIF]*/
			
			value = newData;
			
			/*[IF !Sidecar19-SE]*/
			capacity = currentLength;
			/*[ENDIF]*/
		}
	}
}

/**
 * Returns the Unicode character at the given point.
 * 
 * @param 		index		the character index
 * @return		the Unicode character value at the index
 * 
 * @since		1.5
 */
public synchronized int codePointAt(int index) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (index >= 0 && index < currentLength) {
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			return helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, index));
		} else {
			/*[IF Sidecar19-SE]*/
			int high = helpers.getCharFromArrayByIndex(value, index);
			/*[ELSE]*/
			int high = value[index];
			/*[ENDIF]*/
			
			if ((index + 1) < currentLength && high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
				/*[IF Sidecar19-SE]*/
				int low = helpers.getCharFromArrayByIndex(value, index + 1);
				/*[ELSE]*/
				int low = value[index + 1];
				/*[ENDIF]*/
				
				if (low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
					return 0x10000 + ((high - Character.MIN_HIGH_SURROGATE) << 10) + (low - Character.MIN_LOW_SURROGATE);
				}
			}
			
			return high;
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Returns the Unicode character before the given point.
 * 
 * @param 		index		the character index
 * @return		the Unicode character value before the index
 * 
 * @since		1.5
 */
public synchronized int codePointBefore(int index) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (index > 0 && index <= currentLength) {
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			return helpers.byteToCharUnsigned(helpers.getByteFromArrayByIndex(value, index - 1));
		} else {
			/*[IF Sidecar19-SE]*/
			int low = helpers.getCharFromArrayByIndex(value, index - 1);
			/*[ELSE]*/
			int low = value[index - 1];
			/*[ENDIF]*/
			
			if (index > 1 && low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
				/*[IF Sidecar19-SE]*/
				int high = helpers.getCharFromArrayByIndex(value, index - 2);
				/*[ELSE]*/
				int high = value[index - 2];
				/*[ENDIF]*/
				
				if (high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
					return 0x10000 + ((high - Character.MIN_HIGH_SURROGATE) << 10) + (low - Character.MIN_LOW_SURROGATE);
				}
			}
			
			return low;
		}
	} else {
		throw new StringIndexOutOfBoundsException(index);
	}
}

/**
 * Returns the total Unicode values in the specified range.
 * 
 * @param 		start		first index
 * @param		end			last index
 * @return		the total Unicode values
 * 
 * @since		1.5
 */
public synchronized int codePointCount(int start, int end) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (start >= 0 && start <= end && end <= currentLength) {
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			return end - start;
		} else {
			int count = 0;
			
			for (int i = start; i < end; ++i) {
				/*[IF Sidecar19-SE]*/
				int high = helpers.getCharFromArrayByIndex(value, i);
				/*[ELSE]*/
				int high = value[i];
				/*[ENDIF]*/
				
				if (i + 1 < end && high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
					/*[IF Sidecar19-SE]*/
					int low = helpers.getCharFromArrayByIndex(value, i + 1);
					/*[ELSE]*/
					int low = value[i + 1];
					/*[ENDIF]*/
					
					if (low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
						++i;
					}
				}
				
				++count;
			}
			
			return count;
		}
	} else {
		throw new IndexOutOfBoundsException();
	}
}

/**
 * Returns the index of the code point that was offset by <code>codePointCount</code>.
 * 
 * @param 		start			the position to offset
 * @param		codePointCount	the code point count
 * @return		the offset index
 * 
 * @since		1.5
 */
public synchronized int offsetByCodePoints(int start, int codePointCount) {
	int currentLength = lengthInternalUnsynchronized();
	
	if (start >= 0 && start <= currentLength) {
		// Check if the StringBuffer is compressed
		if (String.enableCompression && count >= 0) {
			int index = start + codePointCount;

			if (index >= currentLength) {
				throw new IndexOutOfBoundsException();
			} else {
				return index;
			}
		} else {
			int index = start;

			if (codePointCount == 0) {
				return start;
			} else if (codePointCount > 0) {
				for (int i = 0; i < codePointCount; ++i) {
					if (index == currentLength) {
						throw new IndexOutOfBoundsException();
					}

					/*[IF Sidecar19-SE]*/
					int high = helpers.getCharFromArrayByIndex(value, index);
					/*[ELSE]*/
					int high = value[index];
					/*[ENDIF]*/

					if ((index + 1) < currentLength && high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
						/*[IF Sidecar19-SE]*/
						int low = helpers.getCharFromArrayByIndex(value, index + 1);
						/*[ELSE]*/
						int low = value[index + 1];
						/*[ENDIF]*/

						if (low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
							index++;
						}
					}

					index++;
				}
			} else {
				for (int i = codePointCount; i < 0; ++i) {
					if (index < 1) {
						throw new IndexOutOfBoundsException();
					}

					/*[IF Sidecar19-SE]*/
					int low = helpers.getCharFromArrayByIndex(value, index - 1);
					/*[ELSE]*/
					int low = value[index - 1];
					/*[ENDIF]*/

					if (index > 1 && low >= Character.MIN_LOW_SURROGATE && low <= Character.MAX_LOW_SURROGATE) {
						/*[IF Sidecar19-SE]*/
						int high = helpers.getCharFromArrayByIndex(value, index - 2);
						/*[ELSE]*/
						int high = value[index - 2];
						/*[ENDIF]*/

						if (high >= Character.MIN_HIGH_SURROGATE && high <= Character.MAX_HIGH_SURROGATE) {
							index--;
						}
					}

					index--;
				}
			}

			return index;
		}
	} else {
		throw new IndexOutOfBoundsException();
	}
}

/**
 * Adds the specified code point to the end of this StringBuffer.
 *
 * @param		codePoint	the code point
 * @return		this StringBuffer
 * 
 * @since 1.5
 */
public synchronized StringBuffer appendCodePoint(int codePoint) {
	if (codePoint >= 0) {
		if (codePoint < 0x10000) {
			return append((char)codePoint);
		} else if (codePoint < 0x110000) {
			// Check if the StringBuffer is compressed
			if (String.enableCompression && count >= 0) {
				decompress(value.length);
			}
			
			int currentLength = lengthInternalUnsynchronized();
			int currentCapacity = capacityInternal();

			int newLength = currentLength + 2;

			if (newLength > currentCapacity) {
				ensureCapacityImpl(newLength);
			}

			codePoint -= 0x10000;

			/*[IF Sidecar19-SE]*/
			helpers.putCharInArrayByIndex(value, currentLength, (char) (Character.MIN_HIGH_SURROGATE + (codePoint >> 10)));
			helpers.putCharInArrayByIndex(value, currentLength + 1, (char) (Character.MIN_LOW_SURROGATE + (codePoint & 0x3ff)));
			/*[ELSE]*/
			value[currentLength] = (char) (Character.MIN_HIGH_SURROGATE + (codePoint >> 10));
			value[currentLength + 1] = (char) (Character.MIN_LOW_SURROGATE + (codePoint & 0x3ff));
			/*[ENDIF]*/

			if (String.enableCompression) {
				count = newLength | uncompressedBit;
			} else {
				count = newLength;
			}
			
			return this;
		}
	}
	
	throw new IllegalArgumentException();
}

/*
 * Returns the character array for this StringBuffer.
 */
/*[IF Sidecar19-SE]*/
byte[] getValue() {
/*[ELSE]*/
char[] getValue() {
/*[ENDIF]*/
	return value;
}

/*[IF Sidecar19-SE]*/
	@Override
	public IntStream chars() {
		/* Following generic CharSequence method invoking need to be updated with optimized implementation specifically for this class */
		return CharSequence.super.chars();
	}
	
	@Override
	public IntStream codePoints() {
		/* Following generic CharSequence method invoking need to be updated with optimized implementation specifically for this class */
		return CharSequence.super.codePoints();
	}
/*[ENDIF]*/

}
