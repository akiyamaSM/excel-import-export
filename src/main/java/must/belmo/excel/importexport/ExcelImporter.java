package must.belmo.excel.importexport;

import must.belmo.excel.importexport.exception.ExcelImporterAccessException;
import must.belmo.excel.importexport.exception.ExcelImporterException;
import must.belmo.excel.importexport.exception.ExcelImporterNoFieldException;
import must.belmo.excel.importexport.utils.CellUtils;
import must.belmo.excel.importexport.utils.TypesUtils;
import must.belmo.excel.importexport.utils.WorkbookUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExcelImporter<T> {
	
	protected Class<T> cls;
	private File file;
	private Map<String, Integer> rowMapper;
	private int sheetNumber;
	private boolean sheetNumberSpecified;
	private Collection<T> items;
	private boolean collectionTypeSpecified;
	private int rowNumber;
	private boolean rowNumberSpecified;
	
	protected ExcelImporter(Class<T> aClass) {
		this.cls = aClass;
		items = new ArrayList<>(); // default
	}
	
	public static <R> ExcelImporter<R> extract(Class<R> aClass) {
		return new ExcelImporter<>(aClass);
	}
	
	public ExcelImporter<T> from(File file) {
		this.file = file;
		return this;
	}
	
	public ExcelImporter<T> from(String file) {
		this.file = new File(file);
		return this;
	}
	
	public ExcelImporter<T> fromAllSheets() {
		sheetNumberSpecified = false;
		return this;
	}
	
	public ExcelImporter<T> toCollection(Collection<T> aCollection) throws ExcelImporterException {
		items = Optional.ofNullable(aCollection)
				.orElseThrow(() -> new ExcelImporterException("The provided collection is null"));
		collectionTypeSpecified = true;
		return this;
	}
	
	public ExcelImporter<T> withColumnsMapper(Map<String, Integer> mapper) {
		this.rowMapper = mapper;
		return this;
	}
	
	public ExcelImporter<T> inSheetNumber(int sheetNumber) {
		this.sheetNumber = sheetNumber;
		sheetNumberSpecified = true;
		return this;
	}
	
	public ExcelImporter<T> atRowNumber(int rowNumber) {
		// access to a row in O(1)
		this.rowNumber = rowNumber;
		rowNumberSpecified = true;
		return this;
	}
	
	public Collection<T> get() throws ExcelImporterException {
		final Workbook workbook = WorkbookUtils.getWorkbookFromFile(file);
		final List<Sheet> sheetList = getSheets(workbook);
		final Collection<T> innerItems = new ArrayList<>();
		final List<Row> rows = getRows(sheetList);
		for (Row row : rows) {
			T object = convertRowToObject(row, rowMapper, cls);
			innerItems.add(object);
		}
		
		if (collectionTypeSpecified) {
			items.addAll(innerItems);
		} else {
			items = innerItems;
		}
		return items;
	}
	
	public List<Sheet> getSheets(Workbook workbook) {
		final List<Sheet> sheetList = new ArrayList<>();
		if (sheetNumberSpecified) {
			sheetList.add(workbook.getSheetAt(this.sheetNumber));
		} else {
			final Iterator<Sheet> sheetIterator = workbook.sheetIterator();
			while (sheetIterator.hasNext()) {
				sheetList.add(sheetIterator.next());
			}
		}
		return sheetList;
	}
	
	public List<Row> getRows(List<Sheet> sheetList) {
		final List<Row> rows = new ArrayList<>();
		for (Sheet sheet : sheetList) {
			if (rowNumberSpecified) {
				rows.add(sheet.getRow(rowNumber));
			} else {
				final Iterator<Row> rowIterator = sheet.rowIterator();
				while (rowIterator.hasNext()) {
					rows.add(rowIterator.next());
				}
			}
		}
		return rows;
	}
	
	private T convertRowToObject(Row row, Map<String, Integer> rowMapper, Class<T> cls) throws ExcelImporterException {
		final T instance = TypesUtils.createInstanceUsingDefaultConstructor(cls);
		for (Map.Entry<String, Integer> entry : rowMapper.entrySet()) {
			final String fieldName = entry.getKey();
			final Integer cellNumber = entry.getValue();
			extractFromRow(row, cls, instance, fieldName, cellNumber);
		}
		return instance;
	}
	
	private void extractFromRow(Row row, Class<T> cls, T target, String toFieldName, Integer fromCellNumber) throws ExcelImporterException {
		final Field field;
		Object cellValue;
		try {
			field = cls.getDeclaredField(toFieldName);
			field.setAccessible(true);
			cellValue = CellUtils.getCellValue(row, fromCellNumber, field.getType());
		} catch (NoSuchFieldException e) {
			throw new ExcelImporterNoFieldException(cls, toFieldName);
		}
		try {
			field.set(target, cellValue);
		} catch (IllegalAccessException e) {
			throw new ExcelImporterAccessException(cls, toFieldName);
		}
	}
}
