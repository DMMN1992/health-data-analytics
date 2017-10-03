package org.snomed.heathanalytics.ingestion.exampledata;

import org.ihtsdo.otf.sqs.service.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.heathanalytics.domain.ClinicalEncounterType;
import org.snomed.heathanalytics.domain.Gender;
import org.snomed.heathanalytics.domain.Patient;
import org.snomed.heathanalytics.ingestion.HealthDataIngestionSource;
import org.snomed.heathanalytics.ingestion.HealthDataIngestionSourceConfiguration;
import org.snomed.heathanalytics.ingestion.HealthDataOutputStream;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.snomed.heathanalytics.ingestion.exampledata.ExampleDataGenerator.DateUtil.dateOfBirthFromAge;

public class ExampleDataGenerator implements HealthDataIngestionSource {

	private final ExampleConceptService concepts;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public ExampleDataGenerator(ExampleConceptService exampleConceptService) {
		this.concepts = exampleConceptService;
	}

	@Override
	public void stream(HealthDataIngestionSourceConfiguration configuration, HealthDataOutputStream healthDataOutputStream) {
		ExampleDataGeneratorConfiguration generatorConfiguration = (ExampleDataGeneratorConfiguration) configuration;
		long start = new Date().getTime();
		List<Exception> exceptions = new ArrayList<>();
		IntStream.range(0, generatorConfiguration.getDemoPatientCount()).parallel().forEach(i -> {
			if (i % 10000 == 0) {
				System.out.print(".");
			}
			try {
				generateExamplePatientAndActs(i + "", healthDataOutputStream);
			} catch (ServiceException e) {
				if (exceptions.size() < 10) {
					exceptions.add(e);
				}
			}
		});
		System.out.println();
		if (!exceptions.isEmpty()) {
			logger.error("There were errors generating patent data.", exceptions.get(0));
		}
		logger.info("Generating patient data took {} seconds.", (new Date().getTime() - start) / 1000);
	}

	private void generateExamplePatientAndActs(String roleId, HealthDataOutputStream healthDataOutputStream) throws ServiceException {
		Patient patient = new Patient();

		//  All patients are over the age of 30 and under the age of 85.
		int age = ThreadLocalRandom.current().nextInt(30, 85);
		patient.setDob(dateOfBirthFromAge(age));

		//  50% of patients are Male.
		if (chance(0.5f)) {
			patient.setGender(Gender.MALE);
		} else {
			patient.setGender(Gender.FEMALE);
		}

		healthDataOutputStream.createPatient(roleId, patient.getName(), patient.getDob(), patient.getGender());

		// Start 2 years ago
		GregorianCalendar date = new GregorianCalendar();
		date.add(Calendar.YEAR, -2);

		// add 1 - 6 months
		date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 6));

		// 10% of patients have diabetes.
		if (chance(0.1f)) {
			healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("420868002"));// Disorder due to type 1 diabetes mellitus

			// After 1 - 2 months
			date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 2));

			// 7% of the diabetic patients also have Peripheral Neuropathy.
			if (chance(0.07f)) {
				healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("302226006"));// Peripheral Neuropathy
			}

			// 10% of the diabetic patients have a Myocardial Infarction.
			if (chance(0.1f)) {
				healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("22298006"));// Myocardial Infarction
			}
		} else {
			// 1% of the non-diabetic patients have Peripheral Neuropathy.
			if (chance(0.01f)) {
				healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("302226006"));// Peripheral Neuropathy
			}
		}

		// After 1 - 3 months
		date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 3));

		// 30 % of patients over 40 years old have hypertension.
		if (age > 40 && chance(0.3f)) {
			healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("38341003"));// Hypertension

			// 50% of patients over 40 with hypertension are prescribed an Antiplatelet agent
			if (chance(0.5f)) {
				// Prescribed an Antiplatelet agent
				healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.MEDICATION, concepts.selectRandomChildOf("108972005"));// Antiplatelet agent (product)

				// After 1 - 6 months
				date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 6));

				// 2% of patients with hypertension who have been prescribed an Antiplatelet agent have a Myocardial Infarction.
				if (chance(0.02f)) {
					healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("22298006"));// Myocardial Infarction
				}
			} else {
				// No medication prescribed

				// After 1 - 6 months
				date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 6));

				// 8% of patients with hypertension who have NOT been prescribed an Antiplatelet agent have a Myocardial Infarction.
				if (chance(0.08f)) {
					healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("22298006"));// Myocardial Infarction
				}
			}
		}

		// After 1 - 2 months
		date.add(Calendar.DAY_OF_YEAR, ThreadLocalRandom.current().nextInt(30, 30 * 2));

		// 5% of all patients over 55 years old have Myocardial Infarction.
		if (age > 55 && chance(0.05f)) {
			healthDataOutputStream.addClinicalEncounter(roleId, date.getTime(), ClinicalEncounterType.FINDING, concepts.selectRandomChildOf("22298006"));// Myocardial Infarction
		}
	}

	private boolean chance(float probability) {
		return probability >= Math.random();
	}

	static final class DateUtil {

		static long millisecondsInAYear;
		static {
			GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(new Date(0));
			calendar.add(Calendar.YEAR, 1);
			millisecondsInAYear = calendar.getTime().getTime();
		}

		static Date dateOfBirthFromAge(int ageInYears) {
			GregorianCalendar date = new GregorianCalendar();
			date.add(Calendar.YEAR, -ageInYears);
			clearTime(date);
			return date.getTime();
		}

		static GregorianCalendar clearTime(GregorianCalendar calendar) {
			calendar.clear(Calendar.HOUR);
			calendar.clear(Calendar.MINUTE);
			calendar.clear(Calendar.SECOND);
			calendar.clear(Calendar.MILLISECOND);
			return calendar;
		}
	}
}
