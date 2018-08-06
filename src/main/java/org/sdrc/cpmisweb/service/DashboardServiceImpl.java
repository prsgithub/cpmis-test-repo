/**
 * 
 */
package org.sdrc.cpmisweb.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sdrc.core.IndicatorClassificationType;
import org.sdrc.core.UnitType;
import org.sdrc.cpmisweb.domain.Area;
import org.sdrc.cpmisweb.domain.Data;
import org.sdrc.cpmisweb.domain.Indicator;
import org.sdrc.cpmisweb.domain.IndicatorClassification;
import org.sdrc.cpmisweb.domain.IndicatorClassificationIndicatorUnitSubgroupMapping;
import org.sdrc.cpmisweb.domain.IndicatorUnitSubgroup;
import org.sdrc.cpmisweb.domain.PublishHistory;
import org.sdrc.cpmisweb.domain.Subgroup;
import org.sdrc.cpmisweb.domain.Timeperiod;
import org.sdrc.cpmisweb.domain.Unit;
import org.sdrc.cpmisweb.model.DataCollectionModel;
import org.sdrc.cpmisweb.model.DataModel;
import org.sdrc.cpmisweb.model.LineSeries;
import org.sdrc.cpmisweb.model.UserModel;
import org.sdrc.cpmisweb.model.ValueObject;
import org.sdrc.cpmisweb.repository.AreaRepository;
import org.sdrc.cpmisweb.repository.DataEntryRepository;
import org.sdrc.cpmisweb.repository.IndicatorClassificationRepository;
import org.sdrc.cpmisweb.repository.IndicatorRepository;
import org.sdrc.cpmisweb.repository.IndicatorUnitSubgroupRepository;
import org.sdrc.cpmisweb.repository.PublishHistoryRepository;
import org.sdrc.cpmisweb.repository.TimePeriodRepository;
import org.sdrc.cpmisweb.util.Constant;
import org.sdrc.cpmisweb.util.HeaderFooter;
import org.sdrc.cpmisweb.util.StateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.html.WebColors;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * @author Biswa Ranjan CPMISWEB
 * 06-Jan-2018 4:52:21 pm
 * Pratyush(pratyush@sdrc.co.in) updated on 25-04-2018
 */
@Service
@Scope("prototype")
@PropertySource(ignoreResourceNotFound = true, value = "file:${catalina.base}/conf/cpmisweb_application.properties")
public class DashboardServiceImpl implements DashboardService{

	@Autowired
    private Environment environment;
	
	
	

	@Autowired
	private DataEntryRepository dataEntryRepository;

	@Autowired
	StateManager stateManager;

	@Autowired
	IndicatorRepository indicatorRepository;

	@Autowired
	IndicatorClassificationRepository indicatorClassificationRepository;

	@Autowired
	IndicatorUnitSubgroupRepository indicatorUnitSubgroupRepository;

	@Autowired
	TimePeriodRepository timePeriodRepository;

	@Autowired
	DataEntryRepository dataRepository;

	@Autowired
	AreaRepository areaRepository;

	@Autowired
	private ResourceBundleMessageSource applicationMessageSource;

	private Map<String, List<LineSeries>> dataByArea = null;

	private Map<String, Integer> ranks = null;

	private Map<String, Map<String, List<ValueObject>>> dataByTPBYSource = null;

	private List<String> topPerformers = null;

	private List<String> bottomPerformers = null;

	@Autowired
	private ServletContext context;

	@Autowired
	private PublishHistoryRepository publishHistoryRepository;

	private static DecimalFormat df = new DecimalFormat(".#");

	@Override
	@Transactional
	public List<ValueObject> fetchIndicators(String sector) {
		IndicatorClassification sectorIC = new IndicatorClassification();
		sectorIC.setIndicatorClassificationId(new Integer(sector));

		List<Object[]> listofIndicators = indicatorRepository.findByIC_Type_(sectorIC, Constant.UNITS_TO_BE_SHOWN_IN_DASHBOARD);

		List<ValueObject> list = new ArrayList<ValueObject>();

		for (int i = 0; i < listofIndicators.size(); i++) {
			Object[] objects = listofIndicators.get(i);

			ValueObject vObject = new ValueObject();
			String indName = "";
			String unitName = "";
			String subName = "";
			for (Object obj : objects) {
				if (obj instanceof Indicator) {
					Indicator utIUS = (Indicator) obj;
					indName = utIUS.getIndicatorName();
					vObject.setKey(Integer.toString(utIUS.getIndicatorId()));
				} else if (obj instanceof Unit) {
					Unit unitEn = (Unit) obj;
					unitName = unitEn.getUnitName();
				} else if (obj instanceof Subgroup) {
					Subgroup subgroupValsEn = (Subgroup) obj;
					subName = subgroupValsEn.getSubgroupVal();
				} else if (obj instanceof IndicatorClassificationIndicatorUnitSubgroupMapping) {
					IndicatorClassificationIndicatorUnitSubgroupMapping utIUS = (IndicatorClassificationIndicatorUnitSubgroupMapping) obj;
					vObject.setDescription(Integer.toString(utIUS.getIndicatorUnitSubgroup().getIndicatorUnitSubgroupId()));
				}
			}
			if(unitName.equals("COL PERCENT"))
				unitName = "PERCENT";
			//if the customer needs to have percentage in indicator list then add '3' in the following constant value(Pratyush)
			if(Constant.UNITS_TO_BE_SHOWN_IN_DASHBOARD.length == 1)
				vObject.setValue(indName + ", " + subName);
			else
				vObject.setValue(indName + ", " + subName + " (" + unitName + ")");
			
			list.add(vObject);
		}
		return list;

	}

	@SuppressWarnings("unchecked")
	@Override
	public JSONArray fetchAllSectors() {
		UserModel user = (UserModel) stateManager.getValue(Constant.USER_PRINCIPAL);
		List<IndicatorClassification> indicatorClassifications = new ArrayList<IndicatorClassification>();
		
		if(user.getUserTypeId() == Constant.STATE_LEVEL_USER_TYPE_ID)
			indicatorClassifications = indicatorClassificationRepository.findByIndicatorClassificationType(IndicatorClassificationType.SC);
		else
			indicatorClassifications = indicatorClassificationRepository
				.findByIndicatorClassificationTypeAndUserType(IndicatorClassificationType.SC, user.getUserTypeId());
		
		if(!user.getSjpuAccess())
			indicatorClassifications = indicatorClassificationRepository.findByIndicatorClassificationTypeAndNameNotLike(IndicatorClassificationType.SC, "%(SJPU%");
		
		JSONArray allSectorArr = new JSONArray();
		for (IndicatorClassification indicatorClassification : indicatorClassifications) {
			JSONObject sectorObj = new JSONObject();
			if (null == indicatorClassification.getParent()) {
				sectorObj.put("key", indicatorClassification.getIndicatorClassificationId());
				sectorObj.put("value", indicatorClassification.getName());
				sectorObj.put("description", -1);
			} else {
				sectorObj.put("key", indicatorClassification.getIndicatorClassificationId());
				sectorObj.put("value", indicatorClassification.getName());
				sectorObj.put("description", indicatorClassification.getParent().getIndicatorClassificationId());
			}
			allSectorArr.add(sectorObj);
		}
		return allSectorArr;
	}

	@Override
	public List<ValueObject> fetchSources(String param) {

		List<IndicatorClassification> classificationsEns = indicatorClassificationRepository.findByIUS_Nid(Integer.parseInt(param));
		List<ValueObject> valueObjects = new ArrayList<>();
		for (IndicatorClassification classificationsEn : classificationsEns) {
			ValueObject object = new ValueObject();
			object.setKey(classificationsEn.getIndicatorClassificationId().toString());
			object.setValue(classificationsEn.getName());
			valueObjects.add(object);
		}
		return valueObjects;
	}

	@Override
	public List<ValueObject> fetchUtTimeperiod(Integer iusNid, Integer SourceNid) {
		List<Timeperiod> utTimeperiods = null;
		List<ValueObject> valueObjects = new ArrayList<>();

		utTimeperiods = timePeriodRepository.findBySource_Nid(iusNid, SourceNid);

		for (Timeperiod utTimeperiod : utTimeperiods) {
			ValueObject object = new ValueObject();
			object.setKey(utTimeperiod.getTimeperiodId().toString());
			object.setValue(utTimeperiod.getTimePeriod());
			valueObjects.add(object);
		}
		return valueObjects;
	}

	@Override
	public DataCollectionModel fetchData(String indicatorId, String sourceId, String parentAreaCode, String timeperiodId, Integer childLevel) throws ParseException {

		dataByArea = null;
		ranks = null;
		dataByTPBYSource = null;
		// get all areas less than or equal to the selected level
		Area[] utAreas = dataRepository.getAreaNid(parentAreaCode, childLevel);

		// get parentArea from the area list
		Area area = getParentUtArea(utAreas, parentAreaCode);

		ArrayList<Area> list = new ArrayList<Area>();
		// get children of the select area.
		getChildren(utAreas, childLevel, area.getAreaId(), list);

		Integer[] areaNids = new Integer[list.size()];

		IndicatorUnitSubgroup ius = indicatorUnitSubgroupRepository.findByIndicatorUnitSubgroupId(Integer.parseInt(indicatorId));

		boolean isIndicatorPositive = ius.getIndicator().isHighIsGood();

		int i = 0;
		for (Area utAreaEn : list) {
			areaNids[i] = utAreaEn.getAreaId();
			i++;
		}

		DataCollectionModel utDataCollection = getUtdataCollection(indicatorId, timeperiodId, sourceId, areaNids, isIndicatorPositive);

		return utDataCollection;
	}

	private Area getParentUtArea(Area[] utAreas, String areaId) {
		Area utAreaen = null;
		for (Area utAreaEn : utAreas) {
			if (utAreaEn.getAreaCode().equalsIgnoreCase(areaId)) {
				utAreaen = utAreaEn;
				break;
			}
		}
		return utAreaen;
	}

	private void getChildren(Area[] utAreas, int i, int parentNid, ArrayList<Area> list) {

		for (Area utAreaEn : utAreas) {
			if (utAreaEn.getParentAreaId() == parentNid) {
				if (utAreaEn.getLevel() == i)
					list.add(utAreaEn);
				else
					getChildren(utAreas, i, utAreaEn.getAreaId(), list);
			}
		}

	}

	private void populateRank(List<Object[]> data, boolean isIndicatorPositive) {

		ranks = new HashMap<String, Integer>();
		topPerformers = new ArrayList<String>();
		bottomPerformers = new ArrayList<String>();
		String finalData;
		if (data != null && !data.isEmpty()) {
			int rank = 0;
			double dataValue = 0.0;
			Area utArea = null;
			Data utData = null;
			if (isIndicatorPositive) {
				for (int index = data.size() - 1; index >= 0; index--) {
					utData = (Data) data.get(index)[0];
					utArea = (Area) data.get(index)[1];

					finalData = utData.getUnit().getUnitId()==1?String.valueOf(Math.round(utData.getPercentage())):String.valueOf(utData.getPercentage());
					// populate the performance by area list
					if (((Data) data.get(0)[0]).getPercentage() == ((Data) data.get(data.size() - 1)[0]).getPercentage()) {
						ranks.put(utArea.getAreaCode(), 1);

						topPerformers.clear();
						bottomPerformers.clear();
					} else {
						if (data.size() >= 6) {
							if (index >= data.size() - 3) {

								topPerformers.add(utArea.getAreaName() + " - " + finalData);
							}
							if (index < 3) {
								bottomPerformers.add(utArea.getAreaName() + " - " + finalData);

							}
						} else if (index <= 2) {
							bottomPerformers.add(utArea.getAreaName() + " - " + (finalData));
						} else {
							topPerformers.add(utArea.getAreaName() + " - " + (finalData));
						}

						if (dataValue == utData.getPercentage() && index != data.size() - 1) {
							ranks.put(utArea.getAreaCode(), rank);
							continue;
						}
						rank++;
						dataValue = utData.getPercentage();

						ranks.put(utArea.getAreaCode(), rank);
					}

				}
			} else {
				for (int index = 0; index < data.size(); index++) {
					utData = (Data) data.get(index)[0];
					utArea = (Area) data.get(index)[1];

					finalData = utData.getUnit().getUnitId()==1?String.valueOf(Math.round(utData.getPercentage())):String.valueOf(utData.getPercentage());
					// populate the performance by area list

					if (((Data) data.get(0)[0]).getPercentage() == ((Data) data.get(data.size() - 1)[0]).getPercentage()) {
						ranks.put(utArea.getAreaCode(), 1);
						topPerformers.clear();
						bottomPerformers.clear();
					} else {
						if (data.size() >= 6) {
							if (index < 3) {

								topPerformers.add(utArea.getAreaName() + " - " + (finalData));
							}
							if (index >= data.size() - 3) {
								bottomPerformers.add(utArea.getAreaName() + " - " + (finalData));
							}
						} else if (index <= 2) {
							topPerformers.add(utArea.getAreaName() + " - " + (finalData));
						} else {
							bottomPerformers.add(utArea.getAreaName() + " - " + (finalData));
						}

						if (dataValue == utData.getPercentage() && index != 0) {
							ranks.put(utArea.getAreaCode(), rank);
							continue;
						}
						rank++;
						dataValue = utData.getPercentage();

						ranks.put(utArea.getAreaCode(), rank);
					}

					ranks.put(utArea.getAreaCode(), rank);
				}
			}

		}
	}

	public Double getFormattedDouble(Double value) {
		Double formattedValue = value != null ? new BigDecimal(value).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue() : null;
		return formattedValue;
	}

	@Transactional(readOnly = true)
	private List<ValueObject> populateLegends(List<Object[]> data, boolean isIndicatorPositive) throws ParseException {
		// TO DO: make this configuration based.
		int range = 5;
		Double minDataValue = null;
		Double maxDataValue = null;
		Integer minDataValue1 = null;
		Integer maxDataValue1 = null;
		String firstslices = Constant.Slices.FIRST_SLICE;
		String secondslices = Constant.Slices.SECOND_SLICE;
		String thirdslices = Constant.Slices.THIRD_SLICE;
		String fourthslices = Constant.Slices.FOUTRH_SLICE;
		String fifthslices = Constant.Slices.FIFTHSLICES;
		String sixthslices = Constant.Slices.SIXTH_SLICES;
		List<ValueObject> list = new ArrayList<ValueObject>();

		if (data != null && !data.isEmpty()) {
			
			if(((Data) data.get(0)[0]).getUnit().getUnitId() != 1){
				minDataValue = getFormattedDouble(((Data) data.get(0)[0]).getPercentage());
				maxDataValue = getFormattedDouble(((Data) data.get(data.size() - 1)[0]).getPercentage());
				Double difference = (maxDataValue - minDataValue) / range;
				if (difference == 0) {
					String firstSliceValue = Double.toString(minDataValue) + " - " + Double.toString(getFormattedDouble(minDataValue + difference));
					list.add(!isIndicatorPositive ? new ValueObject(firstSliceValue, firstslices) : new ValueObject(firstSliceValue, fourthslices));
					list.add(new ValueObject("Not Available", fifthslices));
				} else {
	
					String firstSliceValue = String.valueOf(getFormattedDouble(minDataValue) + 0.0) + " - " + String.valueOf(getFormattedDouble(minDataValue + difference) + 0.0);
					String sixthSliceValue = Double.toString(getFormattedDouble(minDataValue + difference + 0.1)) + " - " + String.valueOf(0.0 + getFormattedDouble(minDataValue + (2 * difference)));
					String secondSliceValue = Double.toString(getFormattedDouble(minDataValue + 2 * difference + 0.1)) + " - " + String.valueOf(0.0 + getFormattedDouble(minDataValue + (3 * difference)));
					String thirdSliceValue = Double.toString(getFormattedDouble(minDataValue + 3 * difference + 0.1)) + " - " + String.valueOf(0.0 + getFormattedDouble(minDataValue + (4 * difference)));
					String fourthSliceValue = Double.toString(getFormattedDouble(minDataValue + 4 * difference + 0.1)) + " - " + Double.toString((maxDataValue));
	
					if (isIndicatorPositive) {
						list.add(new ValueObject(firstSliceValue, firstslices));
						list.add(new ValueObject(sixthSliceValue, sixthslices));
						list.add(new ValueObject(secondSliceValue, secondslices));
						list.add(new ValueObject(thirdSliceValue, thirdslices));
						list.add(new ValueObject(fourthSliceValue, fourthslices));
						list.add(new ValueObject("Not Available", fifthslices));
					} else {
						list.add(new ValueObject(firstSliceValue, fourthslices));
						list.add(new ValueObject(sixthSliceValue, thirdslices));
						list.add(new ValueObject(secondSliceValue, secondslices));
						list.add(new ValueObject(thirdSliceValue, sixthslices));
						list.add(new ValueObject(fourthSliceValue, firstslices));
						list.add(new ValueObject("Not Available", fifthslices));
					}
				}
			}else{
				//when unit is number(Pratyush)
				minDataValue = getFormattedDouble(((Data) data.get(0)[0]).getPercentage());
				minDataValue1 = Integer.parseInt(String.valueOf(Math.round(minDataValue)));
				maxDataValue = getFormattedDouble(((Data) data.get(data.size() - 1)[0]).getPercentage());
				maxDataValue1 = Integer.parseInt(String.valueOf(Math.round(maxDataValue)));
				Double difference = (maxDataValue - minDataValue) / range;
				if (difference == 0) {
					String firstSliceValue = String.valueOf(minDataValue1) + " - " + String.valueOf(minDataValue1);
					list.add(!isIndicatorPositive ? new ValueObject(firstSliceValue, firstslices) : new ValueObject(firstSliceValue, fourthslices));
					list.add(new ValueObject("Not Available", fifthslices));
				} else {
	
					String firstSliceValue = String.valueOf(minDataValue1) + " - " + String.valueOf(Math.round(getFormattedDouble(minDataValue1 + difference)));
					String sixthSliceValue = String.valueOf(Math.round(getFormattedDouble(minDataValue1 + difference + 1))) + " - " + String.valueOf(minDataValue1 + (2 * Math.round(difference)));
					String secondSliceValue = String.valueOf(minDataValue1 + 1 + (2 * Math.round(difference))) + " - " + String.valueOf(minDataValue1 + (3 * Math.round(difference)));
					String thirdSliceValue = String.valueOf(minDataValue1 + 1 +  (3 * Math.round(difference))) + " - " + String.valueOf(minDataValue1 + (4 * Math.round(difference)));
					String fourthSliceValue = String.valueOf(minDataValue1 + 1 +  (4 * Math.round(difference))) + " - " + String.valueOf((maxDataValue1));
	
					if (isIndicatorPositive) {
						list.add(new ValueObject(firstSliceValue, firstslices));
						list.add(new ValueObject(sixthSliceValue, sixthslices));
						list.add(new ValueObject(secondSliceValue, secondslices));
						list.add(new ValueObject(thirdSliceValue, thirdslices));
						list.add(new ValueObject(fourthSliceValue, fourthslices));
						list.add(new ValueObject("Not Available", fifthslices));
					} else {
						list.add(new ValueObject(firstSliceValue, fourthslices));
						list.add(new ValueObject(sixthSliceValue, thirdslices));
						list.add(new ValueObject(secondSliceValue, secondslices));
						list.add(new ValueObject(thirdSliceValue, sixthslices));
						list.add(new ValueObject(fourthSliceValue, firstslices));
						list.add(new ValueObject("Not Available", fifthslices));
					}
				}
			}
		}

		// calculates the rank for the area codes for the selected time-period
		populateRank(data, isIndicatorPositive);

		return list != null && !list.isEmpty() ? list : null;

	}

	@Transactional(readOnly = true)
	public DataCollectionModel getUtdataCollection(String indicatorId, String timePeriodNid, String sourceId, Integer[] areaNid, boolean isIndicatorPositive) throws ParseException {

		DataCollectionModel collection = new DataCollectionModel();

		// this will fetch the data for the selected time-period
		// fetch the data for the selected time-period

		List<Object[]> data = dataRepository.findDataByTimePeriod(Integer.parseInt(indicatorId), Integer.parseInt(timePeriodNid), Integer.parseInt(sourceId), areaNid);

		if (data != null && !data.isEmpty()) {
			List<ValueObject> list = new ArrayList<ValueObject>();

			// this will fetch the data for the selected time-period and
			// populate the legend

			list = populateLegends(data, isIndicatorPositive);
			collection.setLegends(list);
			if ((((Data) data.get(0)[0]).getPercentage() - ((Data) data.get(data.size() - 1)[0]).getPercentage()) == 0) {
				List<String> blankList = new ArrayList<>();
				collection.setTopPerformers(blankList);
				collection.setBottomPerformers(blankList);
			} else {
				collection.setTopPerformers(topPerformers);
				collection.setBottomPerformers(bottomPerformers);
			}

			Data utData = null;
			Area utAreaEn = null;
			Double value = null;

			for (Object[] dataObject : data) {
				DataModel utDataModel = new DataModel();

				utData = (Data) dataObject[0];
				utAreaEn = (Area) dataObject[1];

				value = utData.getPercentage();

				if ((utData.getPercentage()) > 1)
					utDataModel.setValue(df.format(utData.getPercentage()));
				else
					utDataModel.setValue("0" + df.format(utData.getPercentage()));

				utDataModel.setAreaCode(utAreaEn.getAreaCode());
				utDataModel.setAreaName(utAreaEn.getAreaName());
				utDataModel.setAreaNid(utAreaEn.getAreaId());

				utDataModel.setRank(ranks != null && ranks.get(utAreaEn.getAreaCode()) != null ? Integer.toString(ranks.get(utAreaEn.getAreaCode())) : null);
				if (list != null) {
					setCssForDataModel(list, utDataModel, value, indicatorId, sourceId, isIndicatorPositive);
				}
				utDataModel.setUnit("percent");
				if(utData.getUnit().getUnitId() == 1)
					utDataModel.setValue(String.valueOf(Math.round(Double.parseDouble(utDataModel.getValue()))));
				
				collection.dataCollection.add(utDataModel);
			}
		}
		return collection;
	}

	private void setCssForDataModel(List<ValueObject> list, DataModel data, Double value, String indicatorId, String sourceId, boolean isIndicatorPositive) {

		for (int index = 0; index < list.size(); index++) {
			ValueObject vObject = list.get(index);
			String[] vArray = vObject != null ? ((String) vObject.getKey()).split(" - ") : null;

			if (list.size() == 2) {
				if (isIndicatorPositive) {
					data.setCssClass(Constant.Slices.FOUTRH_SLICE);
				} else {
					data.setCssClass(Constant.Slices.FIRST_SLICE);
				}
			}

			else if (index == 5 || (vArray != null && new Double(vArray[0]).doubleValue() <= value && value <= new Double(vArray[1]).doubleValue())) {

				if (isIndicatorPositive) {
					switch (index) {
					case 0:
						data.setCssClass(Constant.Slices.FIRST_SLICE);
						break;
					case 1:
						data.setCssClass(Constant.Slices.SIXTH_SLICES);
						break;
					case 2:
						data.setCssClass(Constant.Slices.SECOND_SLICE);
						break;

					case 3:
						data.setCssClass(Constant.Slices.THIRD_SLICE);
						break;
					case 4:
						data.setCssClass(Constant.Slices.FOUTRH_SLICE);
						break;

					}
				} else {
					switch (index) {
					case 0:
						data.setCssClass(Constant.Slices.FOUTRH_SLICE);
						break;
					case 1:
						data.setCssClass(Constant.Slices.THIRD_SLICE);
						break;
					case 2:
						data.setCssClass(Constant.Slices.SECOND_SLICE);
						break;

					case 3:
						data.setCssClass(Constant.Slices.SIXTH_SLICES);
						break;
					case 4:
						data.setCssClass(Constant.Slices.FIRST_SLICE);
						break;
					}
				}

			}

		}
	}

	@Override
	@Transactional
	public List<List<LineSeries>> fetchChartData(Integer iusNid, Integer areaNid) throws ParseException {
		List<List<LineSeries>> LineCharts = new ArrayList<>();
		List<LineSeries> dataSeries = new ArrayList<>();

		List<Object[]> listData = dataRepository.findData(iusNid, areaNid);
		populateDataByTimePeroid(listData);
		String indicatorId = iusNid.toString();
		IndicatorUnitSubgroup ius = indicatorUnitSubgroupRepository.findByIndicatorUnitSubgroupId(Integer.parseInt(indicatorId));

		boolean isPositveIndicator = ius.getIndicator().isHighIsGood();

		if (listData != null && !listData.isEmpty()) {
			for (int i = 0; i < listData.size(); i++) {
				Object[] dataObjects = listData.get(i);
				
				LineSeries lineChat = new LineSeries();
				for (Object dataObject : dataObjects) {
					if (dataObject instanceof IndicatorClassification) {
						IndicatorClassification classificationsEn = (IndicatorClassification) dataObject;
						lineChat.setSource(classificationsEn.getShortName() == null ? classificationsEn.getName() : classificationsEn.getShortName());
						
					} else if (dataObject instanceof Data) {
						Data data = (Data) dataObject;
						
						if(data.getUnit().getUnitId()==1)
							lineChat.setValue(String.valueOf(Math.round(data.getPercentage())));
						else
							lineChat.setValue((data.getPercentage()) > 1 ? df.format(data.getPercentage()) : "0" + df.format(data.getPercentage()));
					} else if (dataObject instanceof Timeperiod) {
						Timeperiod timeperiod = (Timeperiod) dataObject;
						lineChat.setDate(timeperiod.getTimePeriod());
					}

				}
				for (Object[] dataObject : listData) {
					Area utAreaEn = (Area) dataObject[1];
					IndicatorClassification classificationsEn = (IndicatorClassification) dataObject[3];
					String areaCode = utAreaEn.getAreaCode();
					List<ValueObject> list = dataByTPBYSource.get(areaCode).get(classificationsEn.getShortName());

					BigDecimal percentChange = null;
					
					percentChange = list.size() >= 2 && (Double) (list.get(list.size() - 2).getValue()) > 0 ? new BigDecimal(((Math.abs((Double) (list.get(list.size() - 2).getValue()) - (Double) (list.get(list.size() - 1).getValue()))) / ((Double) (list.get(list.size() - 2).getValue()))) * 100).setScale(1, BigDecimal.ROUND_HALF_UP)
							: new BigDecimal(((Double) (list.get(list.size() - 1).getValue())) * 100);

					if (listData.size() > 1) {
						lineChat.setPercentageChange(percentChange != null ? ((Double) (list.get(listData.size() - 1).getValue()) >= (Double) (list.get(listData.size() - 2).getValue()) ? percentChange.toString() : "-" + percentChange.toString()) : null);
					} else {
						lineChat.setPercentageChange(percentChange.toString());
					}
					lineChat.setIsPositive(percentChange != null && list.size() >= 2 ? (Double) (list.get(listData.size() - 1).getValue()) > (Double) (list.get(listData.size() - 2).getValue()) ? isPositveIndicator ? true : false : isPositveIndicator ? false : true : false);

					if (isPositveIndicator && ((list.size() < 2) || (((Double) (list.get(listData.size() - 1).getValue()) > (Double) (list.get(listData.size() - 2).getValue()))))) {
						lineChat.setCssClass("uptrend");
						lineChat.setIsUpward(true);
					} else if (isPositveIndicator && ((list.size() < 2) || (((Double) (list.get(listData.size() - 1).getValue()) < (Double) (list.get(listData.size() - 2).getValue()))))) {
						lineChat.setCssClass("downtrend");
						lineChat.setIsUpward(false);
					} else if (!isPositveIndicator && ((list.size() < 2) || (((Double) (list.get(listData.size() - 1).getValue()) < (Double) (list.get(listData.size() - 2).getValue()))))) {
						lineChat.setCssClass("uptrend");
						lineChat.setIsUpward(false);
					}
					if (!isPositveIndicator && ((list.size() < 2) || (((Double) (list.get(listData.size() - 1).getValue()) > (Double) (list.get(listData.size() - 2).getValue()))))) {
						lineChat.setCssClass("downtrend");
						lineChat.setIsUpward(true);
					}

				}

				dataSeries.add(lineChat);
			}
		}
		LineCharts.add(dataSeries);
		return LineCharts;

	}

	private void populateDataByTimePeroid(List<Object[]> listData) throws ParseException {
		dataByArea = new HashMap<String, List<LineSeries>>();

		dataByTPBYSource = new HashMap<>();

		if (listData != null && !listData.isEmpty()) {
			Data utData = null;
			Area utAreaEn = null;
			Timeperiod utTimeperiod = null;
			IndicatorClassification classificationsEn = null;

			for (Object[] dataObject : listData) {
				utData = (Data) dataObject[0];
				utAreaEn = (Area) dataObject[1];
				utTimeperiod = (Timeperiod) dataObject[2];
				classificationsEn = (IndicatorClassification) dataObject[3];

				if (dataByTPBYSource.containsKey(utAreaEn.getAreaCode())) {
					Map<String, List<ValueObject>> dataByTPMap = dataByTPBYSource.get(utAreaEn.getAreaCode());

					if (dataByTPMap.containsKey(classificationsEn.getShortName())) {
						List<ValueObject> objects = dataByTPMap.get(classificationsEn.getShortName());
						objects.add(new ValueObject(getFormattedTP(utTimeperiod.getTimePeriod()), utData.getPercentage()));
					} else {
						List<ValueObject> objects = new ArrayList<>();
						objects.add(new ValueObject(getFormattedTP(utTimeperiod.getTimePeriod()), utData.getPercentage()));
						dataByTPMap.put(classificationsEn.getShortName(), objects);
					}

				} else {

					Map<String, List<ValueObject>> dataByTPMap = new HashMap<>();
					List<ValueObject> objects = new ArrayList<>();
					objects.add(new ValueObject(getFormattedTP(utTimeperiod.getTimePeriod()), utData.getPercentage()));
					dataByTPMap.put(classificationsEn.getShortName(), objects);

					dataByTPBYSource.put(utAreaEn.getAreaCode(), dataByTPMap);

				}

				if (dataByArea.containsKey(utAreaEn.getAreaCode())) {
					List<LineSeries> lineSeries = dataByArea.get(utAreaEn.getAreaCode());
					lineSeries.add(new LineSeries(classificationsEn.getShortName(), getFormattedTP(utTimeperiod.getTimePeriod()), utData.getPercentage()));
				} else {
					List<LineSeries> lineSeries = new ArrayList<>();
					lineSeries.add(new LineSeries(classificationsEn.getShortName(), getFormattedTP(utTimeperiod.getTimePeriod()), utData.getPercentage()));
					dataByArea.put(utAreaEn.getAreaCode(), lineSeries);
				}
			}
		}

	}

	public String getFormattedTP(String timePeriod) throws ParseException {

		return timePeriod;
	}

	@Override
	public List<DataModel> fetchPdfData(String indicatorId, String sourceId, String areaId, String timePeriodId, Integer childLevel) {

		List<DataModel> dataModels = new ArrayList<>();
		Area[] utAreas = dataRepository.getAreaNid(areaId, childLevel);

		// get parentArea from the area list
		Area area = getParentUtArea(utAreas, areaId);

		ArrayList<Area> listArea = new ArrayList<Area>();
		// get children of the select area.
		getChildren(utAreas, childLevel, area.getAreaId(), listArea);

		Integer[] areaNids = new Integer[listArea.size()];

		int i = 0;
		for (Area utAreaEn : listArea) {
			areaNids[i] = utAreaEn.getAreaId();
			i++;
		}

		// this will fetch the data for the selected time-period
		// fetch the data for the selected time-period

		List<Object[]> data = dataRepository.findDataByTimePeriod(Integer.parseInt(indicatorId), Integer.parseInt(timePeriodId), Integer.parseInt(sourceId), areaNids);

		if (data != null && !data.isEmpty()) {

			Data utData = null;
			Area utAreaEn = null;
			Double value = null;

			for (Object[] dataObject : data) {
				DataModel utDataModel = new DataModel();

				utData = (Data) dataObject[0];
				utAreaEn = (Area) dataObject[1];

				value = getFormattedDouble(utData != null && utData.getUnit().getUnitType().equals(UnitType.INDEX) ? utData.getPercentage() : utData.getPercentage());

				utDataModel.setValue(value.toString());
				utDataModel.setAreaCode(utAreaEn.getAreaCode());
				utDataModel.setAreaName(utAreaEn.getAreaName());
				utDataModel.setAreaNid(utAreaEn.getAreaId());

				utDataModel.setRank(ranks != null && ranks.get(utAreaEn.getAreaCode()) != null ? Integer.toString(ranks.get(utAreaEn.getAreaCode())) : null);
				dataModels.add(utDataModel);

			}

		}

		return dataModels;

	}

	@SuppressWarnings({ "resource", "unused" })
	@Override
	public String exportPDF(List<String> svgs, String indicatorId, String sourceId, String parentAreaCode, String timeperiodId, Integer childLevel) {

		String file = null;
		try {
			DataCollectionModel valList = fetchData(indicatorId, sourceId, parentAreaCode, timeperiodId, childLevel);

			Indicator indicatorObject = indicatorRepository.findByIndicator_NId(Integer.parseInt(indicatorId));
			Timeperiod timeperiod = timePeriodRepository.findByTimeperiodId(Integer.parseInt(timeperiodId));

			new FileOutputStream(new File(context.getRealPath("") + "/resources/map.svg")).write(svgs.get(2).getBytes());

			Font smallBold = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
			Font dataFont = new Font(Font.FontFamily.HELVETICA, 10);

			Document document = new Document(PageSize.A4.rotate());
			String outputPath = environment.getProperty("outputPathPdf")+new Date().getTime()+".pdf";
			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputPath));
			// setting Header Footer.PLS Refer to
			// org.sdrc.cpisico.util.HeaderFooter
			HeaderFooter headerFooter = new HeaderFooter(context);
			writer.setPageEvent(headerFooter);

			document.open();

			BaseColor cellColor = WebColors.getRGBColor("#E8E3E2");
			BaseColor headerCOlor = WebColors.getRGBColor("#333a3b");
			BaseColor siNoColor = WebColors.getRGBColor("#a6bdd9");

			Paragraph dashboardTitle = new Paragraph();
			dashboardTitle.setAlignment(Element.ALIGN_CENTER);
			dashboardTitle.setSpacingAfter(10);
			Chunk dashboardChunk = new Chunk("Score Card");
			dashboardTitle.add(dashboardChunk);

			Paragraph blankSpace = new Paragraph();
			blankSpace.setAlignment(Element.ALIGN_CENTER);
			blankSpace.setSpacingAfter(10);
			Chunk blankSpaceChunk = new Chunk("          ");
			blankSpace.add(blankSpaceChunk);

			Paragraph numberOfFacility = new Paragraph();
			numberOfFacility.setAlignment(Element.ALIGN_CENTER);
			Chunk numberOfFacilityChunk = new Chunk();
			numberOfFacility.add(numberOfFacilityChunk);

			Paragraph spiderDataParagraph = new Paragraph();
			spiderDataParagraph.setAlignment(Element.ALIGN_CENTER);
			spiderDataParagraph.setSpacingAfter(10);
			Chunk spiderChunk = new Chunk("Indicator : " + indicatorObject.getIndicatorName() + "     Timeperiod : " + timeperiod.getTimePeriod());
			spiderDataParagraph.add(spiderChunk);

			byte[] topBottomImage = svgs.get(0).split(",").length < 2 ? null : Base64.decodeBase64(svgs.get(0).split(",")[1]);
			Image topImage = topBottomImage == null ? null : Image.getInstance(topBottomImage);

			int indentationMap = 0;
			float scalerMap = 0;

			if (topBottomImage != null) {
				scalerMap = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentationMap) / topImage.getWidth()) * 100;

				int indentation1 = 0;
				float scaler1 = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation1) / topImage.getWidth()) * 18;
				if (Integer.parseInt(svgs.get(3)) > 1024) {
					topImage.scalePercent(70);
					topImage.setAbsolutePosition(20, 80);
				} else if (Integer.parseInt(svgs.get(3)) > 1000) {
					topImage.scalePercent(70);
					topImage.setAbsolutePosition(20, 80);
				} else {
					topImage.scalePercent(50);
					topImage.setAbsolutePosition(20, -280);
				}

			}
			byte[] legendImage = Base64.decodeBase64(svgs.get(1).split(",")[1]);
			Image legendImageInstance = Image.getInstance(legendImage);

			// topImage.scalePercent(scalerMap);
			scalerMap = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentationMap) / legendImageInstance.getWidth()) * 100;

			int indentation2 = 0;
			float scaler2 = ((document.getPageSize().getWidth() - document.rightMargin() - indentation2) / legendImageInstance.getWidth()) * 18;

			if (Integer.parseInt(svgs.get(3)) > 1024) {
				legendImageInstance.setAbsolutePosition(240, 80);
				legendImageInstance.scalePercent(70);
			} else if (Integer.parseInt(svgs.get(3)) > 1000) {
				legendImageInstance.setAbsolutePosition(400, 80);
				legendImageInstance.scalePercent(70);
			} else {

				legendImageInstance.setAbsolutePosition(600, -280);
				legendImageInstance.scalePercent(50);

			}

			// legendImageInstance.scalePercent(scalerMap);
			/* googleMapImage.setAbsolutePosition(40,5); */

			Paragraph googleMapParagraph = new Paragraph();
			googleMapParagraph.setAlignment(Element.ALIGN_CENTER);
			googleMapParagraph.setSpacingAfter(10);
			Chunk googleMapChunk = new Chunk();
			googleMapParagraph.add(googleMapChunk);

			String css = "svg {" + "shape-rendering: geometricPrecision;" + "text-rendering:  geometricPrecision;" + "color-rendering: optimizeQuality;" + "image-rendering: optimizeQuality;" + "}";
			File cssFile = File.createTempFile("batik-default-override-", ".css");
			FileUtils.writeStringToFile(cssFile, css);

			String svg_URI_input = Paths.get(new File(context.getRealPath("") + "/resources/map.svg").getPath()).toUri().toURL().toString();
			TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);
			// Step-2: Define OutputStream to PNG Image and attach to
			// TranscoderOutput
			ByteArrayOutputStream png_ostream = new ByteArrayOutputStream();
			TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);
			// Step-3: Create PNGTranscoder and define hints if required
			PNGTranscoder my_converter = new PNGTranscoder();
			// Step-4: Convert and Write output
			my_converter.transcode(input_svg_image, output_png_image);
			png_ostream.flush();

			Image spiderImage = Image.getInstance(png_ostream.toByteArray());

			int indentation = 0;
			float scaler = 0;
			if (Integer.parseInt(svgs.get(3)) > 1024) {
				scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 130;
				spiderImage.setAbsolutePosition(-50, 80);
			}

			else if (Integer.parseInt(svgs.get(3)) > 1000) {
				scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 100;
				spiderImage.setAbsolutePosition(-10, 80);
			}

			else {
				scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 60;
				spiderImage.setAbsolutePosition(160, 20);
			}
			spiderImage.scalePercent(scaler);

			PdfPTable mapDataTable = new PdfPTable(4);

			float[] mapDatacolumnWidths = new float[] { 8f, 30f, 30f, 30f };
			mapDataTable.setWidths(mapDatacolumnWidths);

			PdfPCell mapDataCell0 = new PdfPCell(new Paragraph("Sl.No.", smallBold));
			PdfPCell mapDataCell1 = new PdfPCell(new Paragraph("District ", smallBold));

			PdfPCell mapDataCell3 = new PdfPCell(new Paragraph("Data Value", smallBold));
			PdfPCell mapDataCell4 = new PdfPCell(new Paragraph("Rank", smallBold));

			mapDataCell0.setBackgroundColor(siNoColor);
			mapDataCell1.setBackgroundColor(headerCOlor);

			mapDataCell3.setBackgroundColor(headerCOlor);
			mapDataCell4.setBackgroundColor(headerCOlor);

			mapDataCell1.setHorizontalAlignment(Element.ALIGN_CENTER);
			mapDataCell0.setHorizontalAlignment(Element.ALIGN_CENTER);
			mapDataCell3.setHorizontalAlignment(Element.ALIGN_CENTER);
			mapDataCell4.setHorizontalAlignment(Element.ALIGN_CENTER);

			mapDataCell0.setBorderColor(BaseColor.WHITE);
			mapDataCell1.setBorderColor(BaseColor.WHITE);
			mapDataCell3.setBorderColor(BaseColor.WHITE);
			mapDataCell4.setBorderColor(BaseColor.WHITE);

			mapDataTable.addCell(mapDataCell0);
			mapDataTable.addCell(mapDataCell1);

			mapDataTable.addCell(mapDataCell3);
			mapDataTable.addCell(mapDataCell4);

			int i = 1;
			for (DataModel mapData : valList.getDataCollection()) {

				PdfPCell data0 = new PdfPCell(new Paragraph(Integer.toString(i), dataFont));
				data0.setHorizontalAlignment(Element.ALIGN_CENTER);
				data0.setBorderColor(BaseColor.WHITE);
				PdfPCell data1 = new PdfPCell(new Paragraph(mapData.getAreaName(), dataFont));
				data1.setHorizontalAlignment(Element.ALIGN_LEFT);
				data1.setBorderColor(BaseColor.WHITE);

				PdfPCell data3 = new PdfPCell(new Paragraph(mapData.getValue(), dataFont));
				data3.setHorizontalAlignment(Element.ALIGN_CENTER);
				data3.setBorderColor(BaseColor.WHITE);

				PdfPCell data4 = new PdfPCell(new Paragraph(mapData.getRank(), dataFont));
				data4.setHorizontalAlignment(Element.ALIGN_CENTER);
				data4.setBorderColor(BaseColor.WHITE);

				if (i % 2 == 0) {
					data0.setBackgroundColor(cellColor);
					data1.setBackgroundColor(cellColor);
					data3.setBackgroundColor(cellColor);
					data4.setBackgroundColor(cellColor);
				} else {
					data0.setBackgroundColor(BaseColor.LIGHT_GRAY);
					data1.setBackgroundColor(BaseColor.LIGHT_GRAY);
					data3.setBackgroundColor(BaseColor.LIGHT_GRAY);
					data4.setBackgroundColor(BaseColor.LIGHT_GRAY);
				}

				mapDataTable.addCell(data0);
				mapDataTable.addCell(data1);

				mapDataTable.addCell(data3);
				mapDataTable.addCell(data4);

				i++;

			}
			// Spider Data Table

			document.add(blankSpace);

			document.add(spiderDataParagraph);

			document.add(spiderImage);

			if (null != topImage) {
				document.add(topImage);
			}
			document.add(legendImageInstance);

			document.newPage();
			document.add(mapDataTable);

			document.close();
			file = outputPath;
		}

		catch (Exception e) {

			e.printStackTrace();
		}

		return file;
	}

	@SuppressWarnings("resource")
	@Override
	public String exportPDFLine(List<String> svgs, Integer iusNid, Integer areaNid) {

		String file = null;
		try {

			new FileOutputStream(new File(context.getRealPath("") + "/resources/trendSVG.svg")).write(svgs.get(3).getBytes());

			List<List<LineSeries>> lineSeries = fetchChartData(iusNid, areaNid);

			Font smallBold = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
			Font dataFont = new Font(Font.FontFamily.HELVETICA, 10);

			Document document = new Document(PageSize.A4.rotate());

			String outputPath = environment.getProperty("outputPathPdfLineChart")+new Date().getTime()+".pdf";
//			String outputPath = File.createTempFile("LineChart", ".pdf").toString();// "LineChart.pdf";

			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputPath));
			// setting Header Footer.PLS Refer to
			// org.sdrc.cpisico.util.HeaderFooter
			HeaderFooter headerFooter = new HeaderFooter(context);
			writer.setPageEvent(headerFooter);

			document.open();

			BaseColor cellColor = WebColors.getRGBColor("#E8E3E2");
			BaseColor headerCOlor = WebColors.getRGBColor("#333a3b");
			BaseColor siNoColor = WebColors.getRGBColor("#a6bdd9");

			Paragraph dashboardTitle = new Paragraph();
			dashboardTitle.setAlignment(Element.ALIGN_CENTER);
			dashboardTitle.setSpacingAfter(10);
			Chunk dashboardChunk = new Chunk("Line Chart Score Card");
			dashboardTitle.add(dashboardChunk);

			Paragraph blankSpace = new Paragraph();
			blankSpace.setAlignment(Element.ALIGN_CENTER);
			blankSpace.setSpacingAfter(10);
			Chunk blankSpaceChunk = new Chunk("          ");
			blankSpace.add(blankSpaceChunk);

			Paragraph numberOfFacility = new Paragraph();
			numberOfFacility.setAlignment(Element.ALIGN_CENTER);
			Chunk numberOfFacilityChunk = new Chunk();
			numberOfFacility.add(numberOfFacilityChunk);

			Paragraph spiderDataParagraph = new Paragraph();
			spiderDataParagraph.setAlignment(Element.ALIGN_CENTER);
			spiderDataParagraph.setSpacingAfter(10);
			Chunk spiderChunk = new Chunk("Indicator : " + svgs.get(2) + "  Area : " + svgs.get(1));
			spiderDataParagraph.add(spiderChunk);

			byte[] lineChartImage = Base64.decodeBase64(svgs.get(0).split(",")[1]);
			Image lineTrendChartImage = Image.getInstance(lineChartImage);

			if (Integer.parseInt(svgs.get(4)) > 1024) {
				lineTrendChartImage.scalePercent(80);
				lineTrendChartImage.setAbsolutePosition(170, 150);
			} else if (Integer.parseInt(svgs.get(4)) > 1000) {
				lineTrendChartImage.scalePercent(80);
				lineTrendChartImage.setAbsolutePosition(200, 150);
			} else {
				lineTrendChartImage.scalePercent(90);
				lineTrendChartImage.setAbsolutePosition(180, 100);
			}

			String css = "svg {" + "shape-rendering: geometricPrecision;" + "text-rendering:  geometricPrecision;" + "color-rendering: optimizeQuality;" + "image-rendering: optimizeQuality;" + "}";
			File cssFile = File.createTempFile("batik-default-override-", ".css");
			FileUtils.writeStringToFile(cssFile, css);

			String svg_URI_input = Paths.get(new File(context.getRealPath("") + "/resources/trendSVG.svg").getPath()).toUri().toURL().toString();
			TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);
			// Step-2: Define OutputStream to PNG Image and attach to
			// TranscoderOutput
			ByteArrayOutputStream png_ostream = new ByteArrayOutputStream();
			TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);
			// Step-3: Create PNGTranscoder and define hints if required
			PNGTranscoder my_converter = new PNGTranscoder();
			// Step-4: Convert and Write output
			my_converter.transcode(input_svg_image, output_png_image);
			png_ostream.flush();

			Image spiderImage = Image.getInstance(png_ostream.toByteArray());

			int indentation = 0;
			float scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 60;
			if (Integer.parseInt(svgs.get(4)) > 1024) {
				spiderImage.scalePercent(scaler);
				spiderImage.setAbsolutePosition(175, 165);
			} else if (Integer.parseInt(svgs.get(4)) > 1000) {
				scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 45;
				spiderImage.scalePercent(scaler);
				spiderImage.setAbsolutePosition(200, 160);
			} else {
				scaler = ((document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin() - indentation) / spiderImage.getWidth()) * 45;
				spiderImage.scalePercent(scaler);
				spiderImage.setAbsolutePosition(200, 150);
			}
			// legendImageInstance.scalePercent(scalerMap);
			/* googleMapImage.setAbsolutePosition(40,5); */

			Paragraph googleMapParagraph = new Paragraph();
			googleMapParagraph.setAlignment(Element.ALIGN_CENTER);
			googleMapParagraph.setSpacingAfter(10);
			Chunk googleMapChunk = new Chunk();
			googleMapParagraph.add(googleMapChunk);

			PdfPTable mapDataTable = new PdfPTable(3);

			float[] mapDatacolumnWidths = new float[] { 8f, 15f, 15f };
			mapDataTable.setWidths(mapDatacolumnWidths);

			PdfPCell mapDataCell0 = new PdfPCell(new Paragraph("Sl.No.", smallBold));
			PdfPCell mapDataCell1 = new PdfPCell(new Paragraph("Data Value ", smallBold));

			PdfPCell mapDataCell3 = new PdfPCell(new Paragraph("Time Period", smallBold));

			mapDataCell0.setBackgroundColor(siNoColor);
			mapDataCell1.setBackgroundColor(headerCOlor);
			mapDataCell3.setBackgroundColor(headerCOlor);

			mapDataCell1.setHorizontalAlignment(Element.ALIGN_CENTER);
			mapDataCell0.setHorizontalAlignment(Element.ALIGN_CENTER);
			mapDataCell3.setHorizontalAlignment(Element.ALIGN_CENTER);

			mapDataCell0.setBorderColor(BaseColor.WHITE);
			mapDataCell1.setBorderColor(BaseColor.WHITE);
			mapDataCell3.setBorderColor(BaseColor.WHITE);

			mapDataTable.addCell(mapDataCell0);
			mapDataTable.addCell(mapDataCell1);

			mapDataTable.addCell(mapDataCell3);

			int i = 1;
			for (LineSeries seriesData : lineSeries.get(0)) {

				PdfPCell data0 = new PdfPCell(new Paragraph(Integer.toString(i), dataFont));
				data0.setHorizontalAlignment(Element.ALIGN_CENTER);
				data0.setBorderColor(BaseColor.WHITE);
				PdfPCell data1 = new PdfPCell(new Paragraph(seriesData.getValue().toString(), dataFont));
				data1.setHorizontalAlignment(Element.ALIGN_LEFT);
				data1.setBorderColor(BaseColor.WHITE);

				PdfPCell data3 = new PdfPCell(new Paragraph(seriesData.getDate(), dataFont));
				data3.setHorizontalAlignment(Element.ALIGN_CENTER);
				data3.setBorderColor(BaseColor.WHITE);

				if (i % 2 == 0) {
					data0.setBackgroundColor(cellColor);
					data1.setBackgroundColor(cellColor);
					data3.setBackgroundColor(cellColor);

				} else {
					data0.setBackgroundColor(BaseColor.LIGHT_GRAY);
					data1.setBackgroundColor(BaseColor.LIGHT_GRAY);
					data3.setBackgroundColor(BaseColor.LIGHT_GRAY);

				}

				mapDataTable.addCell(data0);
				mapDataTable.addCell(data1);

				mapDataTable.addCell(data3);

				i++;

			}
			// Spider Data Table

			document.add(blankSpace);

			document.add(spiderDataParagraph);

			document.add(lineTrendChartImage);

			document.add(spiderImage);

			document.newPage();
			document.add(mapDataTable);

			document.close();
			file = outputPath;

		} catch (Exception e) {

			e.printStackTrace();
		}
		return file;
	}

	@Override
	@Transactional
	public boolean publishData() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();

		int month = now.getMonthValue();
		if (month == 1) {
			month = 12;
			year = year - 1;

		} else {
			month = month - 1;
		}
		String monthString = "";
		if (month >= 10) {
			monthString = month + "";
		} else {
			monthString = "0" + month;
		}
		PublishHistory history = new PublishHistory();
		history.setDataBeingPublishedForMonth(month);
		history.setDataBeingPublishedForYear(year);
		history.setDataPublishedDate(new Date());

		history = publishHistoryRepository.save(history);

		List<Area> areas = areaRepository.findAllByParentAreaId(2);
		Timeperiod timePeriod = timePeriodRepository.findByTimePeriod(year + "." + monthString);
		List<Data> datas = dataEntryRepository.findByTimePeriodAndAreaIn(timePeriod, areas);

		dataEntryRepository.save(datas);

		return history != null;

	}

	@Override
	@Transactional
	public boolean publishData(int agencyId, int year, int month, String monthString) {

		List<Area> areas = areaRepository.findAllByParentAreaId(2);
		Timeperiod timePeriod = timePeriodRepository.findByTimePeriod(year + "." + monthString);
		List<Data> datas = dataEntryRepository.findByTimePeriodAndAreaIn(timePeriod, areas);

		PublishHistory history = publishHistoryRepository.findByDataBeingPublishedForMonthAndDataBeingPublishedForYear(month, year);
		if (history == null) {
			history = new PublishHistory();
			history.setDataBeingPublishedForMonth(month);
			history.setDataBeingPublishedForYear(year);
			history.setDataPublishedDate(new Date());
			history = publishHistoryRepository.save(history);
		}

		dataEntryRepository.save(datas);

		return true;

	}

}
