package cc.factorie.app.classify.backend

import cc.factorie.app.classify.NaiveBayesClassifierTrainer
import cc.factorie.la.DenseTensor2
import cc.factorie.variable._
import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit._

import scala.collection.mutable


class TestNaiveBayes extends JUnitSuite with cc.factorie.util.FastLogging {

  // define the person gender as classification label
  object GenderDomain extends CategoricalDomain[String] {
    value("male")
    value("female")
    freeze()
  }

  // classification features
  object PersonFeaturesDomain extends CategoricalVectorDomain[String]
  class PersonFeatures extends BinaryFeatureVectorVariable[String] {
    override def domain: CategoricalVectorDomain[String] = PersonFeaturesDomain
  }

  // Person can be have features, and a gender label
  class Person(gender: String, largeFoot: Boolean, longHair: Boolean)
    extends LabeledCategoricalVariable(gender) {

    def domain = GenderDomain

    val features: PersonFeatures = {
      val results = new PersonFeatures
      if (this.largeFoot) {
        results += "LargeFoot"
      }
      if (this.longHair) {
        results += "LongHair"
      }
      results
    }
  }

  @Test
  def testItemizedModel(): Unit = {

    // Person(gender, largeFoot, longHair)
    val p1 = new Person("male",   true,  false)
    val p2 = new Person("male",   true,  false)
    val p3 = new Person("male",   true,  true)
    val p4 = new Person("female", false, true)
    val p5 = new Person("female", false, true)
    val p6 = new Person("female", false, true)
    val p7 = new Person("female", true,  true)

    val people = new mutable.ArrayBuffer[Person]()
    people ++= Seq(p1, p2, p3, p4, p5, p6, p7)

    // specify 0 to disable smoothing
    val trainer = new NaiveBayesClassifierTrainer(0)

    val classifier = trainer.train(people, (person: Person) => person.features)

    // what we expect:
    // p(male|largeFoot)   = 3/4 = 0.75
    // p(male|longhair)    = 1/5 = 0.2
    // p(female|largeFoot) = 1/4 = 0.25
    // p(female|longhair)  = 4/5 = 0.8
    val expected = new DenseTensor2(Array(Array(math.log(0.75), math.log(0.25)), Array(math.log(0.2), math.log(0.8))))
    assertArrayEquals(expected.toArray, classifier.weights.value.toArray, 0.001)

    // p(male|largeFoot&longHair) = 0.75 * 0.2 = 0.15
    // p(female|largeFoot&longHair) = 0.25 * 0.8 = 0.2
    val c = classifier.classify(p7)
    assertArrayEquals(Array(math.log(0.15), math.log(0.2)), c.prediction.toArray, 0.001)
  }

}
