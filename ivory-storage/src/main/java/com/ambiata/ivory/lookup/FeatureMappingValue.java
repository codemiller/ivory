/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.ambiata.ivory.lookup;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureMappingValue implements org.apache.thrift.TBase<FeatureMappingValue, FeatureMappingValue._Fields>, java.io.Serializable, Cloneable, Comparable<FeatureMappingValue> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("FeatureMappingValue");

  private static final org.apache.thrift.protocol.TField NS_FIELD_DESC = new org.apache.thrift.protocol.TField("ns", org.apache.thrift.protocol.TType.I32, (short)1);
  private static final org.apache.thrift.protocol.TField NEW_NAME_FIELD_DESC = new org.apache.thrift.protocol.TField("newName", org.apache.thrift.protocol.TType.STRING, (short)2);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new FeatureMappingValueStandardSchemeFactory());
    schemes.put(TupleScheme.class, new FeatureMappingValueTupleSchemeFactory());
  }

  public int ns; // required
  public String newName; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    NS((short)1, "ns"),
    NEW_NAME((short)2, "newName");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // NS
          return NS;
        case 2: // NEW_NAME
          return NEW_NAME;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __NS_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.NS, new org.apache.thrift.meta_data.FieldMetaData("ns", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I32)));
    tmpMap.put(_Fields.NEW_NAME, new org.apache.thrift.meta_data.FieldMetaData("newName", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(FeatureMappingValue.class, metaDataMap);
  }

  public FeatureMappingValue() {
  }

  public FeatureMappingValue(
    int ns,
    String newName)
  {
    this();
    this.ns = ns;
    setNsIsSet(true);
    this.newName = newName;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public FeatureMappingValue(FeatureMappingValue other) {
    __isset_bitfield = other.__isset_bitfield;
    this.ns = other.ns;
    if (other.isSetNewName()) {
      this.newName = other.newName;
    }
  }

  public FeatureMappingValue deepCopy() {
    return new FeatureMappingValue(this);
  }

  @Override
  public void clear() {
    setNsIsSet(false);
    this.ns = 0;
    this.newName = null;
  }

  public int getNs() {
    return this.ns;
  }

  public FeatureMappingValue setNs(int ns) {
    this.ns = ns;
    setNsIsSet(true);
    return this;
  }

  public void unsetNs() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __NS_ISSET_ID);
  }

  /** Returns true if field ns is set (has been assigned a value) and false otherwise */
  public boolean isSetNs() {
    return EncodingUtils.testBit(__isset_bitfield, __NS_ISSET_ID);
  }

  public void setNsIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __NS_ISSET_ID, value);
  }

  public String getNewName() {
    return this.newName;
  }

  public FeatureMappingValue setNewName(String newName) {
    this.newName = newName;
    return this;
  }

  public void unsetNewName() {
    this.newName = null;
  }

  /** Returns true if field newName is set (has been assigned a value) and false otherwise */
  public boolean isSetNewName() {
    return this.newName != null;
  }

  public void setNewNameIsSet(boolean value) {
    if (!value) {
      this.newName = null;
    }
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case NS:
      if (value == null) {
        unsetNs();
      } else {
        setNs((Integer)value);
      }
      break;

    case NEW_NAME:
      if (value == null) {
        unsetNewName();
      } else {
        setNewName((String)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case NS:
      return Integer.valueOf(getNs());

    case NEW_NAME:
      return getNewName();

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case NS:
      return isSetNs();
    case NEW_NAME:
      return isSetNewName();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof FeatureMappingValue)
      return this.equals((FeatureMappingValue)that);
    return false;
  }

  public boolean equals(FeatureMappingValue that) {
    if (that == null)
      return false;

    boolean this_present_ns = true;
    boolean that_present_ns = true;
    if (this_present_ns || that_present_ns) {
      if (!(this_present_ns && that_present_ns))
        return false;
      if (this.ns != that.ns)
        return false;
    }

    boolean this_present_newName = true && this.isSetNewName();
    boolean that_present_newName = true && that.isSetNewName();
    if (this_present_newName || that_present_newName) {
      if (!(this_present_newName && that_present_newName))
        return false;
      if (!this.newName.equals(that.newName))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public int compareTo(FeatureMappingValue other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = Boolean.valueOf(isSetNs()).compareTo(other.isSetNs());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNs()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.ns, other.ns);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetNewName()).compareTo(other.isSetNewName());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetNewName()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.newName, other.newName);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("FeatureMappingValue(");
    boolean first = true;

    sb.append("ns:");
    sb.append(this.ns);
    first = false;
    if (!first) sb.append(", ");
    sb.append("newName:");
    if (this.newName == null) {
      sb.append("null");
    } else {
      sb.append(this.newName);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class FeatureMappingValueStandardSchemeFactory implements SchemeFactory {
    public FeatureMappingValueStandardScheme getScheme() {
      return new FeatureMappingValueStandardScheme();
    }
  }

  private static class FeatureMappingValueStandardScheme extends StandardScheme<FeatureMappingValue> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, FeatureMappingValue struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // NS
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.ns = iprot.readI32();
              struct.setNsIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // NEW_NAME
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.newName = iprot.readString();
              struct.setNewNameIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, FeatureMappingValue struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      oprot.writeFieldBegin(NS_FIELD_DESC);
      oprot.writeI32(struct.ns);
      oprot.writeFieldEnd();
      if (struct.newName != null) {
        oprot.writeFieldBegin(NEW_NAME_FIELD_DESC);
        oprot.writeString(struct.newName);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class FeatureMappingValueTupleSchemeFactory implements SchemeFactory {
    public FeatureMappingValueTupleScheme getScheme() {
      return new FeatureMappingValueTupleScheme();
    }
  }

  private static class FeatureMappingValueTupleScheme extends TupleScheme<FeatureMappingValue> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, FeatureMappingValue struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetNs()) {
        optionals.set(0);
      }
      if (struct.isSetNewName()) {
        optionals.set(1);
      }
      oprot.writeBitSet(optionals, 2);
      if (struct.isSetNs()) {
        oprot.writeI32(struct.ns);
      }
      if (struct.isSetNewName()) {
        oprot.writeString(struct.newName);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, FeatureMappingValue struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(2);
      if (incoming.get(0)) {
        struct.ns = iprot.readI32();
        struct.setNsIsSet(true);
      }
      if (incoming.get(1)) {
        struct.newName = iprot.readString();
        struct.setNewNameIsSet(true);
      }
    }
  }

}
