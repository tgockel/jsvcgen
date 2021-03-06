import de.johoop.jacoco4sbt._

import JacocoPlugin._

jacoco.settings

Keys.fork              in jacoco.Config := false

parallelExecution      in jacoco.Config := false

jacoco.outputDirectory in jacoco.Config := file("target/jacoco")

jacoco.reportFormats   in jacoco.Config := Seq( XMLReport(encoding = "utf-8"),
                                                ScalaHTMLReport(withBranchCoverage = true))

jacoco.includes        in jacoco.Config := Seq("com/solidfire/**")

