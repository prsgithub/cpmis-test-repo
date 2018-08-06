package org.sdrc.cpmisweb.springdatarepository;

import java.util.Date;
import java.util.List;

import org.sdrc.cpmisweb.domain.Timeperiod;
import org.sdrc.cpmisweb.repository.TimePeriodRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.RepositoryDefinition;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@RepositoryDefinition(domainClass=Timeperiod.class,idClass=Integer.class)
public interface SpringDataTimePeriodRepository extends TimePeriodRepository{

	@Override
	@Query("SELECT time FROM Timeperiod time WHERE time.timeperiodId " +
			" IN (SELECT distinct data.timePeriod FROM Data data  WHERE data.indicatorUnitSubgroup.indicatorUnitSubgroupId = :iusNid "
			+ "AND data.source.indicatorClassificationId = :sourceNid ) order by time.timeperiodId desc")
	List<Timeperiod> findBySource_Nid(@Param("iusNid") Integer iusNid,@Param("sourceNid") Integer sourceNid) throws DataAccessException;

	@Override
	@Query("SELECT time FROM Timeperiod time WHERE time.timeperiodId " +
			" IN (SELECT distinct data.timePeriod FROM Data data  WHERE data.indicatorUnitSubgroup.indicatorUnitSubgroupId = :iusNid "
			+ "AND data.source.indicatorClassificationId = :sourceNid and data.published=true)")
	public List<Timeperiod> findBySource_NidForPublicView(@Param("iusNid")Integer iusNid, @Param("sourceNid") Integer sourceNid);

	@Override
	@Query(value="select t.* from time_period t "
			+ "left outer join data d on t.timeperiod_id=d.time_period_id_fk "
			+ " inner join indicator i on d.indicator_id_fk=i.indicator_id "
			+ "where i.agency_id_fk= :agencyId and d.indicator_id_fk is not null group by t.timeperiod_id",nativeQuery=true)
	
	public List<Timeperiod> findTimePeriodsPresentForDataOfMyAgencyAllPublishedAndUnPublished(@Param("agencyId") int agencyId);
	
	
	@Override
	@Query(value="select t.* from time_period t where t.periodicity=1 and t.timeperiod_id > :id "
			+ "order by timeperiod_id asc limit 1",nativeQuery=true)
	public Timeperiod findNextTimeperiodOfGivenId(@Param("id") Integer id);
	
	@Override
	@Modifying
	@Transactional
	@Query(value="update time_period set quarter_id=:timeperiodId where start_date between "
			+ ":startDate and :endDate and periodicity=1",nativeQuery=true)
	public void update(@Param("timeperiodId") Integer timeperiodId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);
}
