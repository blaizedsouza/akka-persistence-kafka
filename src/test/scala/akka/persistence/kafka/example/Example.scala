package akka.persistence.kafka.example

import java.util.Properties

import scala.collection.immutable.Seq

import akka.actor._
import akka.persistence.{PersistentRepr, PersistentActor}
import akka.persistence.kafka.{DefaultEventDecoder, Event, EventTopicMapper}
import akka.persistence.kafka.server.{TestServerConfig, TestServer}
import akka.serialization.SerializationExtension

import com.typesafe.config.ConfigFactory

import kafka.consumer.{Consumer, ConsumerConfig}
import kafka.serializer.{DefaultDecoder, StringDecoder}

class ExampleProcessor(val persistenceId: String) extends PersistentActor {
  import ExampleProcessor.Increment

  var state: Int = 0
  def receiveCommand: Receive = {
    case i: Increment => persist(i)(update)
  }

  def receiveRecover: Receive = {
    case i: Increment => update(i)

  }

  def update(i: Increment): Unit = {
    state += i.value
    println(s"state = ${state}")
  }
}

class ExampleEventTopicMapper extends EventTopicMapper {
  def topicsFor(event: Event): Seq[String] = event.persistenceId match {
    case "a" => List("topic-a-1", "topic-a-2")
    case "b" => List("topic-b")
    case _   => Nil
  }
}

object ExampleProcessor extends App {
  case class Increment(value: Int)

  val system = ActorSystem("example", ConfigFactory.load("example"))
  val actorA = system.actorOf(Props(new ExampleProcessor("a")))

  actorA ! Increment(2)
  actorA ! Increment(3)
}

object ExampleConsumer extends App {
  val props = new Properties()
  props.put("group.id", "consumer-1")
  props.put("zookeeper.connect", "localhost:2181")
  props.put("auto.offset.reset", "smallest")
  props.put("auto.commit.enable", "false")

  val consConn = Consumer.create(new ConsumerConfig(props))
  val streams = consConn.createMessageStreams(Map("topic-a-2" -> 1),
    keyDecoder = new StringDecoder, valueDecoder = new DefaultEventDecoder)

  streams("topic-a-2")(0).foreach { mm =>
    val event: Event = mm.message
    println(s"consumed ${event}")
  }
}

object ExampleJournalConsumer extends App {
  val props = new Properties()
  props.put("group.id", "consumer-2")
  props.put("zookeeper.connect", "localhost:2181")
  props.put("auto.offset.reset", "smallest")
  props.put("auto.commit.enable", "false")

  val system = ActorSystem("example")
  val extension = SerializationExtension(system)

  val consConn = Consumer.create(new ConsumerConfig(props))
  val streams = consConn.createMessageStreams(Map("a" -> 1),
    keyDecoder = new StringDecoder, valueDecoder = new DefaultDecoder)

  streams("a")(0).foreach { mm =>
    val persistent: PersistentRepr = extension.deserialize(mm.message, classOf[PersistentRepr]).get
    println(s"consumed ${persistent}")
  }
}

object ExampleServer extends App {
  new TestServer(TestServerConfig.load("example"))
}