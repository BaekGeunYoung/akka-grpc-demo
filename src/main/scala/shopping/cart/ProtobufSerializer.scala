package shopping.cart

import akka.serialization.SerializerWithStringManifest
import shopping.cart.ShoppingCart.{CheckedOut, ItemAdded, ItemQuantityAdjusted, ItemRemoved}

import java.time.Instant

/**
 * Marker trait for serialization with Jackson CBOR.
 * Enabled in serialization.conf `akka.actor.serialization-bindings` (via application.conf).
 */
object ProtobufSerializer {

  final val manifest_ShoppingCart$ItemAdded     = classOf[ItemAdded].getName
  final val manifest_ShoppingCart$ItemQuantityAdjusted  = classOf[ItemQuantityAdjusted].getName
  final val manifest_ShoppingCart$ItemRemoved  = classOf[ItemRemoved].getName
  final val manifest_ShoppingCart$CheckedOut  = classOf[CheckedOut].getName
}

class ProtobufSerializer extends SerializerWithStringManifest {

  import ProtobufSerializer._

  /**
   * Serializer identifier
   * @return Int
   */
  override def identifier: Int = 9002

  /**
   * Get manifest (getClass.getName)
   * @param o Object
   * @return String
   */
  override def manifest(o: AnyRef): String = o.getClass.getName

  /**
   * Convert from object to binary
   * @param o Object
   * @return Array
   */
  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case z: ItemAdded => proto.ItemAdded(z.cartId, z.itemId, z.quantity).toByteArray
    case z: ItemQuantityAdjusted => proto.ItemQuantityAdjusted(z.cartId, z.itemId, z.newQuantity).toByteArray
    case z: ItemRemoved => proto.ItemRemoved(z.cartId, z.itemId).toByteArray
    case z: CheckedOut => proto.CheckedOut(z.cartId).toByteArray
  }

  /**
   * Convert from binary to object
   * @param bytes Array
   * @param manifest String
   * @return Object
   */
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case `manifest_ShoppingCart$ItemAdded` =>
        val p = proto.ItemAdded.parseFrom(bytes)
        ItemAdded(p.cartId, p.itemId, p.quantity)

      case `manifest_ShoppingCart$ItemQuantityAdjusted` =>
        val p = proto.ItemQuantityAdjusted.parseFrom(bytes)
        ItemQuantityAdjusted(p.cartId, p.itemId, p.quantity, p.quantity)

      case `manifest_ShoppingCart$ItemRemoved` =>
        val p = proto.ItemRemoved.parseFrom(bytes)
        ItemRemoved(p.cartId, p.itemId, 0)

      case `manifest_ShoppingCart$CheckedOut` =>
        val p = proto.CheckedOut.parseFrom(bytes)
        CheckedOut(p.cartId, Instant.now())
    }
  }
}